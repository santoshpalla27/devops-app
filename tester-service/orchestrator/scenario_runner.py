import asyncio
import logging
from datetime import datetime
from typing import Dict, Any
from dataclasses import dataclass

from observers.api_client import APIObserver
from observers.kafka_listener import KafkaObserver
from observers.prometheus_scraper import PrometheusObserver
from assertions import StateAssertion, PolicyAssertion, MetricsAssertion, EventAssertion
from .action_executor import ActionExecutor
from .scenario_loader import Scenario

logger = logging.getLogger(__name__)


@dataclass
class TestResult:
    """Result of a scenario execution."""
    scenario_name: str
    status: str  # PASSED, FAILED, SKIPPED
    duration: float
    assertions_passed: int
    assertions_failed: int
    details: Dict[str, Any]


class ScenarioRunner:
    """Orchestrates scenario execution."""
    
    def __init__(
        self,
        api_observer: APIObserver,
        kafka_observer: KafkaObserver,
        prometheus_observer: PrometheusObserver,
        action_executor: ActionExecutor
    ):
        self.api_observer = api_observer
        self.kafka_observer = kafka_observer
        self.prometheus_observer = prometheus_observer
        self.action_executor = action_executor
        
        # Assertion engines
        self.state_assertions = StateAssertion()
        self.policy_assertions = PolicyAssertion()
        self.metrics_assertions = MetricsAssertion()
        self.event_assertions = EventAssertion()
    
    async def run_scenario(self, scenario: Scenario) -> TestResult:
        """Run a complete scenario."""
        logger.info(f"🚀 Starting scenario: {scenario.name}")
        start_time = datetime.now()
        
        # Start Kafka observer (non-async)
        self.kafka_observer.start_observing()
        
        # Start API observer
        systems = self._extract_systems(scenario)
        observer_task = asyncio.create_task(
            self.api_observer.start_observing(systems)
        )
        
        try:
            # Execute actions
            for i, action in enumerate(scenario.actions):
                logger.info(f"  📌 Action {i+1}/{len(scenario.actions)}: {action.get('type')}")
                await self.action_executor.execute(action)
            
            # Wait for expectations to be met
            await asyncio.sleep(5)  # Give system time to settle
            
            # Stop observers
            await self.api_observer.stop_observing()
            self.kafka_observer.stop_observing()
            
            # Cancel observer task
            observer_task.cancel()
            
            # Evaluate assertions
            results = await self._evaluate_expectations(scenario)
            
            # Calculate duration
            duration = (datetime.now() - start_time).total_seconds()
            
            # Determine status
            passed = sum(1 for r in results if r['passed'])
            failed = sum(1 for r in results if not r['passed'])
            status = 'PASSED' if failed == 0 else 'FAILED'
            
            logger.info(f"✅ Scenario {scenario.name}: {status} ({passed}/{passed+failed} assertions)")
            
            return TestResult(
                scenario_name=scenario.name,
                status=status,
                duration=duration,
                assertions_passed=passed,
                assertions_failed=failed,
                details={'assertions': results}
            )
            
        except Exception as e:
            logger.error(f"❌ Scenario failed with error: {e}")
            return TestResult(
                scenario_name=scenario.name,
                status='FAILED',
                duration=(datetime.now() - start_time).total_seconds(),
                assertions_passed=0,
                assertions_failed=1,
                details={'error': str(e)}
            )
    
    async def _evaluate_expectations(self, scenario: Scenario) -> list:
        """Evaluate all expectations."""
        results = []
        expectations = scenario.expectations
        
        # Get collected data
        api_data = self.api_observer.get_collected_data()
        kafka_events = self.kafka_observer.get_all_events()
        
        # Extract systems from scenario
        systems = self._extract_systems(scenario)
        
        # State assertions
        for system in systems:
            transitions = self.api_observer.get_state_transitions(system)
            passed, message = self.state_assertions.assert_no_illegal_state(
                api_data['state_history']
            )
            results.append({'type': 'state', 'passed': passed, 'message': message})
        
        # Policy assertions
        if 'policies' in expectations:
            for policy_exp in expectations['policies']:
                policy_id = policy_exp.get('policy_id')
                should_fire = policy_exp.get('should_fire', True)
                
                if should_fire:
                    passed, message = self.policy_assertions.assert_policy_fired(
                        api_data['policy_executions'],
                        policy_id
                    )
                else:
                    passed, message = self.policy_assertions.assert_policy_not_fired(
                        api_data['policy_executions'],
                        policy_id
                    )
                
                results.append({'type': 'policy', 'passed': passed, 'message': message})
        
        # Event assertions
        if 'events' in expectations:
            for event_exp in expectations['events']:
                event_type = event_exp.get('type')
                count = event_exp.get('count', 1)
                
                passed, message = self.event_assertions.assert_event_count(
                    kafka_events,
                    event_type,
                    count
                )
                results.append({'type': 'event', 'passed': passed, 'message': message})
        
        return results
    
    def _extract_systems(self, scenario: Scenario) -> list:
        """Extract system names from scenario."""
        systems = set()
        
        # From actions
        for action in scenario.actions:
            if action.get('type') == 'CHAOS':
                systems.add(action.get('system'))
        
        # From expectations
        if 'states' in scenario.expectations:
            for state_exp in scenario.expectations['states']:
                if isinstance(state_exp, dict):
                    systems.add(state_exp.get('system'))
        
        return list(filter(None, systems))

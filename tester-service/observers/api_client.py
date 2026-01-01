import aiohttp
import asyncio
from typing import Dict, List, Optional
from datetime import datetime
import logging

logger = logging.getLogger(__name__)


class APIObserver:
    """Observes the control plane via REST API."""
    
    def __init__(self, base_url: str):
        self.base_url = base_url
        self.state_history = []
        self.policy_executions = []
        self.chaos_experiments = []
        self.drift_records = []
        self.contract_violations = []
        self.observing = False
        
    async def start_observing(self, systems: List[str]):
        """Start observing specified systems."""
        self.observing = True
        tasks = [
            self._observe_states(systems),
            self._observe_policies(),
            self._observe_chaos(),
            self._observe_drift(),
            self._observe_contracts()
        ]
        await asyncio.gather(*tasks)
    
    async def stop_observing(self):
        """Stop all observations."""
        self.observing = False
    
    async def _observe_states(self, systems: List[str]):
        """Poll system states."""
        async with aiohttp.ClientSession() as session:
            while self.observing:
                try:
                    for system in systems:
                        async with session.get(
                            f"{self.base_url}/api/system/status"
                        ) as response:
                            if response.status == 200:
                                data = await response.json()
                                # Extract state for this system
                                system_status = data.get('systems', {}).get(system)
                                if system_status:
                                    self.state_history.append({
                                        'time': datetime.now(),
                                        'system': system,
                                        'state': system_status.get('state'),
                                        'latency': system_status.get('latency'),
                                        'retries': system_status.get('retries')
                                    })
                except Exception as e:
                    logger.error(f"Error observing states: {e}")
                
                await asyncio.sleep(1)
    
    async def _observe_policies(self):
        """Poll policy executions."""
        async with aiohttp.ClientSession() as session:
            while self.observing:
                try:
                    async with session.get(
                        f"{self.base_url}/api/policies/executions?limit=50"
                    ) as response:
                        if response.status == 200:
                            executions = await response.json()
                            for exec in executions:
                                if exec not in self.policy_executions:
                                    self.policy_executions.append(exec)
                except Exception as e:
                    logger.error(f"Error observing policies: {e}")
                
                await asyncio.sleep(2)
    
    async def _observe_chaos(self):
        """Poll chaos experiments."""
        async with aiohttp.ClientSession() as session:
            while self.observing:
                try:
                    async with session.get(
                        f"{self.base_url}/api/chaos/experiments"
                    ) as response:
                        if response.status == 200:
                            experiments = await response.json()
                            self.chaos_experiments = experiments
                except Exception as e:
                    logger.error(f"Error observing chaos: {e}")
                
                await asyncio.sleep(2)
    
    async def _observe_drift(self):
        """Poll drift records."""
        async with aiohttp.ClientSession() as session:
            while self.observing:
                try:
                    async with session.get(
                        f"{self.base_url}/api/reconciliation/drift"
                    ) as response:
                        if response.status == 200:
                            drift = await response.json()
                            self.drift_records = drift
                except Exception as e:
                    logger.error(f"Error observing drift: {e}")
                
                await asyncio.sleep(3)
    
    async def _observe_contracts(self):
        """Poll contract violations."""
        async with aiohttp.ClientSession() as session:
            while self.observing:
                try:
                    async with session.get(
                        f"{self.base_url}/api/system/contracts/violations"
                    ) as response:
                        if response.status == 200:
                            violations = await response.json()
                            self.contract_violations = violations
                except Exception as e:
                    logger.error(f"Error observing contracts: {e}")
                
                await asyncio.sleep(3)
    
    def get_state_transitions(self, system: str) -> List[str]:
        """Get sequence of state transitions for a system."""
        transitions = []
        prev_state = None
        
        for record in self.state_history:
            if record['system'] == system:
                current_state = record['state']
                if prev_state and current_state != prev_state:
                    transitions.append(f"{prev_state} -> {current_state}")
                prev_state = current_state
        
        return transitions
    
    def get_collected_data(self) -> Dict:
        """Return all collected observation data."""
        return {
            'state_history': self.state_history,
            'policy_executions': self.policy_executions,
            'chaos_experiments': self.chaos_experiments,
            'drift_records': self.drift_records,
            'contract_violations': self.contract_violations
        }

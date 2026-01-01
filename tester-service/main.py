#!/usr/bin/env python3
"""
DevOps Control Plane - Tester Service
Black-box scenario-driven testing for system behavior validation.
"""

import asyncio
import logging
import sys
import argparse
from pathlib import Path
from colorama import init, Fore, Style

from config.constants import CONTROL_PLANE_URL, KAFKA_BOOTSTRAP_SERVERS, PROMETHEUS_URL
from observers import APIObserver, KafkaObserver, PrometheusObserver
from orchestrator.action_executor import ActionExecutor
from orchestrator.scenario_runner import ScenarioRunner
from orchestrator.scenario_loader import load_scenario, load_all_scenarios

# Initialize colorama
init(autoreset=True)

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def print_banner():
    """Print welcome banner."""
    print(f"\n{Fore.CYAN}{'='*60}")
    print(f"{Fore.CYAN}  DevOps Control Plane - Tester Service")
    print(f"{Fore.CYAN}  Scenario-Driven Black-Box Testing")
    print(f"{Fore.CYAN}{'='*60}{Style.RESET_ALL}\n")


def print_result(result):
    """Print test result."""
    status_color = Fore.GREEN if result.status == 'PASSED' else Fore.RED
    status_symbol = '✅' if result.status == 'PASSED' else '❌'
    
    print(f"\n{status_symbol} {status_color}{result.status}{Style.RESET_ALL}: {result.scenario_name}")
    print(f"   Duration: {result.duration:.2f}s")
    print(f"   Assertions: {result.assertions_passed} passed, {result.assertions_failed} failed")
    
    if result.details.get('assertions'):
        print(f"\n   {Fore.YELLOW}Assertion Details:{Style.RESET_ALL}")
        for assertion in result.details['assertions']:
            print(f"     {assertion['message']}")


async def run_single_scenario(scenario_path: str, config: dict):
    """Run a single scenario."""
    print_banner()
    
    # Initialize components
    api_observer = APIObserver(config['control_plane_url'])
    kafka_observer = KafkaObserver(
        config['kafka_bootstrap'],
        'controlplane-events'
    )
    prometheus_observer = PrometheusObserver(config['prometheus_url'])
    action_executor = ActionExecutor(config['control_plane_url'])
    
    runner = ScenarioRunner(
        api_observer,
        kafka_observer,
        prometheus_observer,
        action_executor
    )
    
    # Load and run scenario
    scenario = load_scenario(scenario_path)
    result = await runner.run_scenario(scenario)
    
    # Print result
    print_result(result)
    
    return 0 if result.status == 'PASSED' else 1


async def run_all_scenarios(scenarios_dir: str, config: dict, fail_fast: bool = False):
    """Run all scenarios in a directory."""
    print_banner()
    
    # Initialize components
    api_observer = APIObserver(config['control_plane_url'])
    kafka_observer = KafkaObserver(
        config['kafka_bootstrap'],
        'controlplane-events'
    )
    prometheus_observer = PrometheusObserver(config['prometheus_url'])
    action_executor = ActionExecutor(config['control_plane_url'])
    
    runner = ScenarioRunner(
        api_observer,
        kafka_observer,
        prometheus_observer,
        action_executor
    )
    
    # Load all scenarios
    scenarios = load_all_scenarios(scenarios_dir)
    logger.info(f"Found {len(scenarios)} scenarios")
    
    results = []
    for scenario in scenarios:
        result = await runner.run_scenario(scenario)
        results.append(result)
        print_result(result)
        
        if fail_fast and result.status == 'FAILED':
            logger.error("Stopping due to failure (fail-fast mode)")
            break
    
    # Summary
    passed = sum(1 for r in results if r.status == 'PASSED')
    failed = sum(1 for r in results if r.status == 'FAILED')
    
    print(f"\n{Fore.CYAN}{'='*60}")
    print(f"{Fore.CYAN}  Summary: {passed} passed, {failed} failed")
    print(f"{Fore.CYAN}{'='*60}{Style.RESET_ALL}\n")
    
    return 0 if failed == 0 else 1


def main():
    parser = argparse.ArgumentParser(
        description='DevOps Control Plane Tester Service'
    )
    
    parser.add_argument(
        'command',
        choices=['run', 'ci'],
        help='Command to execute'
    )
    
    parser.add_argument(
        '--scenario',
        help='Path to scenario file (for run command)'
    )
    
    parser.add_argument(
        '--scenarios-dir',
        default='scenarios',
        help='Directory containing scenarios (for ci command)'
    )
    
    parser.add_argument(
        '--fail-fast',
        action='store_true',
        help='Stop on first failure'
    )
    
    parser.add_argument(
        '--control-plane-url',
        default=CONTROL_PLANE_URL,
        help='Control plane base URL'
    )
    
    parser.add_argument(
        '--kafka-bootstrap',
        default=KAFKA_BOOTSTRAP_SERVERS,
        help='Kafka bootstrap servers'
    )
    
    parser.add_argument(
        '--prometheus-url',
        default=PROMETHEUS_URL,
        help='Prometheus URL'
    )
    
    args = parser.parse_args()
    
    config = {
        'control_plane_url': args.control_plane_url,
        'kafka_bootstrap': args.kafka_bootstrap,
        'prometheus_url': args.prometheus_url
    }
    
    if args.command == 'run':
        if not args.scenario:
            logger.error("--scenario required for 'run' command")
            return 1
        
        exit_code = asyncio.run(run_single_scenario(args.scenario, config))
    
    elif args.command == 'ci':
        exit_code = asyncio.run(
            run_all_scenarios(args.scenarios_dir, config, args.fail_fast)
        )
    
    return exit_code


if __name__ == '__main__':
    sys.exit(main())

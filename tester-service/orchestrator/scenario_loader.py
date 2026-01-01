import yaml
from typing import Dict, List, Any
from dataclasses import dataclass
import logging

logger = logging.getLogger(__name__)


@dataclass
class Scenario:
    """Represents a test scenario."""
    name: str
    description: str
    timeout: int
    preconditions: List[Dict[str, Any]]
    actions: List[Dict[str, Any]]
    expectations: Dict[str, Any]
    validation: List[str]
    
    @staticmethod
    def from_yaml(yaml_path: str) -> 'Scenario':
        """Load scenario from YAML file."""
        with open(yaml_path, 'r') as f:
            data = yaml.safe_load(f)
        
        return Scenario(
            name=data.get('name', 'Unknown'),
            description=data.get('description', ''),
            timeout=data.get('timeout', 120),
            preconditions=data.get('preconditions', []),
            actions=data.get('actions', []),
            expectations=data.get('expectations', {}),
            validation=data.get('validation', [])
        )


def load_scenario(path: str) -> Scenario:
    """Load a scenario from file."""
    logger.info(f"Loading scenario from {path}")
    return Scenario.from_yaml(path)


def load_all_scenarios(directory: str) -> List[Scenario]:
    """Load all scenarios from a directory."""
    import os
    import glob
    
    scenarios = []
    pattern = os.path.join(directory, '*.yaml')
    
    for yaml_file in glob.glob(pattern):
        try:
            scenario = load_scenario(yaml_file)
            scenarios.append(scenario)
        except Exception as e:
            logger.error(f"Failed to load {yaml_file}: {e}")
    
    return scenarios

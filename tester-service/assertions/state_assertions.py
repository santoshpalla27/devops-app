from typing import List, Dict
import logging

logger = logging.getLogger(__name__)


class StateAssertion:
    """Assertions for state machine behavior."""
    
    def assert_transition_sequence(
        self,
        actual_transitions: List[str],
        expected_transitions: List[str]
    ) -> tuple[bool, str]:
        """Verify state transitions happened in expected order."""
        if actual_transitions == expected_transitions:
            return True, f"✅ Transitions matched: {expected_transitions}"
        
        return False, f"❌ Expected {expected_transitions}, got {actual_transitions}"
    
    def assert_contains_transition(
        self,
        transitions: List[str],
        expected: str
    ) -> tuple[bool, str]:
        """Verify a specific transition occurred."""
        if expected in transitions:
            return True, f"✅ Transition '{expected}' found"
        
        return False, f"❌ Transition '{expected}' not found in {transitions}"
    
    def assert_no_illegal_state(
        self,
        state_history: List[Dict]
    ) -> tuple[bool, str]:
        """Ensure no invalid states occurred."""
        illegal_states = ['UNKNOWN', 'ERROR', 'INVALID']
        
        for record in state_history:
            state = record.get('state')
            if state in illegal_states:
                return False, f"❌ Illegal state found: {state}"
        
        return True, "✅ No illegal states detected"
    
    def assert_final_state(
        self,
        state_history: List[Dict],
        system: str,
        expected_state: str
    ) -> tuple[bool, str]:
        """Verify system ended in expected state."""
        system_states = [r for r in state_history if r['system'] == system]
        
        if not system_states:
            return False, f"❌ No state history for {system}"
        
        final_state = system_states[-1]['state']
        
        if final_state == expected_state:
            return True, f"✅ Final state is {expected_state}"
        
        return False, f"❌ Expected final state {expected_state}, got {final_state}"

from typing import List, Dict
import logging

logger = logging.getLogger(__name__)


class PolicyAssertion:
    """Assertions for policy behavior."""
    
    def assert_policy_fired(
        self,
        policy_executions: List[Dict],
        policy_id: str
    ) -> tuple[bool, str]:
        """Verify a specific policy was triggered."""
        fired = any(
            exec.get('policyId') == policy_id or exec.get('policyName') == policy_id
            for exec in policy_executions
        )
        
        if fired:
            return True, f"✅ Policy '{policy_id}' was triggered"
        
        return False, f"❌ Policy '{policy_id}' was not triggered"
    
    def assert_policy_not_fired(
        self,
        policy_executions: List[Dict],
        policy_id: str
    ) -> tuple[bool, str]:
        """Verify a policy was NOT triggered."""
        fired = any(
            exec.get('policyId') == policy_id or exec.get('policyName') == policy_id
            for exec in policy_executions
        )
        
        if not fired:
            return True, f"✅ Policy '{policy_id}' was not triggered (as expected)"
        
        return False, f"❌ Policy '{policy_id}' was triggered (unexpected)"
    
    def assert_policy_success(
        self,
        policy_executions: List[Dict],
        policy_id: str
    ) -> tuple[bool, str]:
        """Verify policy executed successfully."""
        executions = [
            e for e in policy_executions
            if e.get('policyId') == policy_id or e.get('policyName') == policy_id
        ]
        
        if not executions:
            return False, f"❌ Policy '{policy_id}' not found"
        
        successful = all(e.get('success', False) for e in executions)
        
        if successful:
            return True, f"✅ Policy '{policy_id}' executed successfully"
        
        return False, f"❌ Policy '{policy_id}' had failures"

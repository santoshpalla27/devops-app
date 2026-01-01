from typing import Dict
import logging

logger = logging.getLogger(__name__)


class MetricsAssertion:
    """Assertions for metrics thresholds."""
    
    def assert_threshold(
        self,
        metric_name: str,
        actual_value: float,
        operator: str,
        threshold_value: float
    ) -> tuple[bool, str]:
        """Verify a metric meets a threshold."""
        operators = {
            '<': lambda a, t: a < t,
            '<=': lambda a, t: a <= t,
            '>': lambda a, t: a > t,
            '>=': lambda a, t: a >= t,
            '==': lambda a, t: a == t,
            '!=': lambda a, t: a != t
        }
        
        if operator not in operators:
            return False, f"❌ Invalid operator: {operator}"
        
        result = operators[operator](actual_value, threshold_value)
        
        if result:
            return True, f"✅ {metric_name}: {actual_value} {operator} {threshold_value}"
        
        return False, f"❌ {metric_name}: {actual_value} NOT {operator} {threshold_value}"
    
    def assert_retry_count_below(
        self,
        actual_retries: int,
        max_retries: int
    ) -> tuple[bool, str]:
        """Verify retry count stayed below threshold."""
        return self.assert_threshold(
            'retry_count',
            actual_retries,
            '<',
            max_retries
        )
    
    def assert_latency_below(
        self,
        actual_latency: float,
        max_latency: float
    ) -> tuple[bool, str]:
        """Verify latency stayed below threshold."""
        return self.assert_threshold(
            'latency_ms',
            actual_latency,
            '<',
            max_latency
        )

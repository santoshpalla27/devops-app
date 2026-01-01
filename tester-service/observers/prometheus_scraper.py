import aiohttp
import logging
from typing import Dict, Optional

logger = logging.getLogger(__name__)


class PrometheusObserver:
    """Observes metrics from Prometheus."""
    
    def __init__(self, prometheus_url: str):
        self.prometheus_url = prometheus_url
    
    async def query_metric(self, metric_name: str, labels: Dict[str, str] = None) -> Optional[float]:
        """Query a specific metric from Prometheus."""
        labels_str = ""
        if labels:
            labels_str = "{" + ",".join([f'{k}="{v}"' for k, v in labels.items()]) + "}"
        
        query = f"{metric_name}{labels_str}"
        
        async with aiohttp.ClientSession() as session:
            try:
                async with session.get(
                    f"{self.prometheus_url}/api/v1/query",
                    params={'query': query}
                ) as response:
                    if response.status == 200:
                        data = await response.json()
                        result = data.get('data', {}).get('result', [])
                        if result:
                            return float(result[0]['value'][1])
            except Exception as e:
                logger.error(f"Error querying Prometheus: {e}")
        
        return None
    
    async def get_retry_count(self, system: str) -> Optional[int]:
        """Get retry count for a system."""
        value = await self.query_metric(
            'controlplane_connection_retries',
            {'system': system}
        )
        return int(value) if value is not None else None
    
    async def get_latency(self, system: str) -> Optional[float]:
        """Get latency for a system."""
        return await self.query_metric(
            'controlplane_connection_latency',
            {'system': system}
        )
    
    async def get_circuit_state(self, system: str) -> Optional[int]:
        """Get circuit breaker state (0=closed, 1=open)."""
        value = await self.query_metric(
            'controlplane_circuit_state',
            {'system': system}
        )
        return int(value) if value is not None else None
    
    async def get_policy_execution_count(self, policy: str) -> Optional[int]:
        """Get count of policy executions."""
        value = await self.query_metric(
            'controlplane_policy_executions_total',
            {'policy': policy}
        )
        return int(value) if value is not None else None

import { useState, useCallback } from 'react';
import {
    SystemHealth,
    ConnectionStatus,
    TopologyInfo,
    CircuitBreakerStatus,
    MetricsSummary,
    ActionResult,
} from '../types';

const API_BASE = import.meta.env.VITE_API_URL || '/api';

export function useApi() {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const fetchWithError = useCallback(async <T>(url: string): Promise<T> => {
        setLoading(true);
        setError(null);
        try {
            const response = await fetch(url);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            return await response.json();
        } catch (e) {
            const message = e instanceof Error ? e.message : 'Unknown error';
            setError(message);
            throw e;
        } finally {
            setLoading(false);
        }
    }, []);

    const postAction = useCallback(async (url: string): Promise<ActionResult> => {
        setLoading(true);
        setError(null);
        try {
            const response = await fetch(url, { method: 'POST' });
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            return await response.json();
        } catch (e) {
            const message = e instanceof Error ? e.message : 'Unknown error';
            setError(message);
            return { success: false, message };
        } finally {
            setLoading(false);
        }
    }, []);

    // Health APIs
    const getSystemHealth = useCallback(
        () => fetchWithError<SystemHealth>(`${API_BASE}/health`),
        [fetchWithError]
    );

    const getConnectionStatus = useCallback(
        (system: string) =>
            fetchWithError<ConnectionStatus>(`${API_BASE}/health/${system}`),
        [fetchWithError]
    );

    const getCircuitBreakers = useCallback(
        () =>
            fetchWithError<Record<string, CircuitBreakerStatus>>(
                `${API_BASE}/health/circuit-breakers`
            ),
        [fetchWithError]
    );

    // Topology APIs
    const getAllTopologies = useCallback(
        () =>
            fetchWithError<Record<string, TopologyInfo>>(`${API_BASE}/actions/topology`),
        [fetchWithError]
    );

    const getTopology = useCallback(
        (system: string) =>
            fetchWithError<TopologyInfo>(`${API_BASE}/actions/topology/${system}`),
        [fetchWithError]
    );

    // Metrics APIs
    const getMetricsSummary = useCallback(
        () => fetchWithError<MetricsSummary>(`${API_BASE}/metrics/summary`),
        [fetchWithError]
    );

    const getLatencies = useCallback(
        () => fetchWithError<Record<string, number>>(`${API_BASE}/metrics/latencies`),
        [fetchWithError]
    );

    // Action APIs
    const forceReconnect = useCallback(
        (system: string) => postAction(`${API_BASE}/actions/reconnect/${system}`),
        [postAction]
    );

    const refreshTopology = useCallback(
        () => postAction(`${API_BASE}/actions/refresh-topology`),
        [postAction]
    );

    const resetCircuitBreaker = useCallback(
        (system: string) =>
            postAction(`${API_BASE}/actions/circuit-breaker/${system}/reset`),
        [postAction]
    );

    return {
        loading,
        error,
        // Health
        getSystemHealth,
        getConnectionStatus,
        getCircuitBreakers,
        // Topology
        getAllTopologies,
        getTopology,
        // Metrics
        getMetricsSummary,
        getLatencies,
        // Actions
        forceReconnect,
        refreshTopology,
        resetCircuitBreaker,
    };
}

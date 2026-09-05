export function formatDuration(durationMillis: number): string {
  if (durationMillis < 1000) return `${durationMillis}ms`;
  if (durationMillis < 60_000) return `${(durationMillis / 1000).toFixed(1)}s`;
  const minutes = Math.floor(durationMillis / 60_000);
  const seconds = (durationMillis % 60_000) / 1000;
  return `${minutes}m ${seconds.toFixed(1)}s`;
}

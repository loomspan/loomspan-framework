const prefix = "#/pair/";
const pairingShape = /^[A-Za-z0-9_-]{43}$/;

export function consumePairingFragment(
  location: Pick<Location, "hash" | "pathname" | "search"> = window.location,
  history: Pick<History, "replaceState"> = window.history,
): string | undefined {
  if (!location.hash.startsWith(prefix)) return undefined;
  const candidate = location.hash.slice(prefix.length);
  history.replaceState(null, "", `${location.pathname}${location.search}`);
  return pairingShape.test(candidate) ? candidate : undefined;
}

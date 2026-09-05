import { useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { useTarget } from "../target/TargetProvider";
import { targetScopeParameter } from "./scope";

export function useScopeBoundRoute() {
  const { target } = useTarget();
  const navigate = useNavigate();
  const [searchParameters] = useSearchParams();
  const currentScopeID = target.status.targetScopeId;
  const routeScopeID = searchParameters.get(targetScopeParameter);
  const matchesCurrentScope = Boolean(
    currentScopeID && routeScopeID === currentScopeID,
  );

  useEffect(() => {
    if (!matchesCurrentScope) {
      navigate("/", {
        replace: true,
        state: { staleTargetScope: true },
      });
    }
  }, [matchesCurrentScope, navigate]);

  return matchesCurrentScope;
}

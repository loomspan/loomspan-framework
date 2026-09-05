export function formatJavaSkillMethod(method: string | undefined): string {
  if (!method) return "";
  const parameterStart = method.indexOf("(");
  const declaration = (parameterStart >= 0 ? method.slice(0, parameterStart) : method).trim();
  const qualifiedMethod = declaration.slice(declaration.lastIndexOf(" ") + 1);
  return `${qualifiedMethod}()`;
}

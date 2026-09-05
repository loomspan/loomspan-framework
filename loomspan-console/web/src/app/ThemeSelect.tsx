import { useEffect, useState } from "react";
import {
  Button,
  Label,
  ListBox,
  ListBoxItem,
  Popover,
  Select,
  SelectValue,
} from "react-aria-components";

type Theme = "system" | "light" | "dark";

const storageKey = "loomspan.console.theme";
const themes: ReadonlyArray<{ id: Theme; label: string }> = [
  { id: "system", label: "Follow system" },
  { id: "light", label: "Light" },
  { id: "dark", label: "Dark" },
];

function initialTheme(): Theme {
  const stored = sessionStorage.getItem(storageKey);
  return stored === "light" || stored === "dark" || stored === "system" ? stored : "system";
}

function applyTheme(theme: Theme) {
  if (theme === "system") {
    document.documentElement.removeAttribute("data-theme");
  } else {
    document.documentElement.dataset.theme = theme;
  }
}

export function ThemeSelect() {
  const [theme, setTheme] = useState<Theme>(initialTheme);
  useEffect(() => applyTheme(theme), [theme]);

  return (
    <Select
      aria-label="Console theme"
      className="theme-select"
      selectedKey={theme}
      onSelectionChange={(key) => {
        const next = String(key) as Theme;
        if (!themes.some((candidate) => candidate.id === next)) return;
        sessionStorage.setItem(storageKey, next);
        setTheme(next);
      }}
    >
      <Label>Theme</Label>
      <Button className="theme-button">
        <SelectValue />
        <span aria-hidden="true">⌄</span>
      </Button>
      <Popover className="theme-popover">
        <ListBox>
          {themes.map((candidate) => (
            <ListBoxItem className="theme-option" id={candidate.id} key={candidate.id}>
              {candidate.label}
            </ListBoxItem>
          ))}
        </ListBox>
      </Popover>
    </Select>
  );
}

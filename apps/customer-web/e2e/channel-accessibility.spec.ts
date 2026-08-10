import { expect, test, type Page } from "@playwright/test";

type AuditResult = {
  readonly duplicateIds: readonly string[];
  readonly formControlsWithoutNames: readonly string[];
  readonly interactiveWithoutNames: readonly string[];
  readonly invalidAriaReferences: readonly string[];
  readonly imagesWithoutAlternatives: readonly string[];
  readonly contrastFailures: readonly string[];
  readonly mainCount: number;
  readonly h1Count: number;
  readonly lang: string;
};

const corePages = [
  { name: "customer held-transfer", url: "http://localhost:3001/transfers/new", heading: "New Transfer" },
  { name: "call-center held-transfer", url: "http://localhost:3008/workspace", heading: "Agent Workspace" }
] as const;

for (const target of corePages) {
  test(`${target.name} passes the automated WCAG 2.2 AA core-page audit`, async ({ page }) => {
    await page.goto(target.url);
    await expect(page.getByRole("heading", { level: 1, name: target.heading })).toBeVisible();
    const audit = await auditCorePage(page);

    expect(audit.lang).toMatch(/^[a-z]{2}(?:-|$)/u);
    expect(audit.mainCount).toBe(1);
    expect(audit.h1Count).toBe(1);
    expect(audit.duplicateIds).toEqual([]);
    expect(audit.formControlsWithoutNames).toEqual([]);
    expect(audit.interactiveWithoutNames).toEqual([]);
    expect(audit.invalidAriaReferences).toEqual([]);
    expect(audit.imagesWithoutAlternatives).toEqual([]);
    expect(audit.contrastFailures).toEqual([]);

    await page.keyboard.press("Tab");
    await expect.poll(() => page.evaluate(() => document.activeElement !== document.body)).toBe(true);
    await expect.poll(() => page.evaluate(() => {
      const active = document.activeElement;
      if (!(active instanceof HTMLElement)) return false;
      const style = getComputedStyle(active);
      return style.outlineStyle !== "none" && Number.parseFloat(style.outlineWidth) >= 2;
    })).toBe(true);
  });
}

async function auditCorePage(page: Page): Promise<AuditResult> {
  return page.evaluate(() => {
    const visible = (element: Element) => {
      const style = getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.display !== "none" && style.visibility !== "hidden" && Number(style.opacity) > 0 && rect.width > 0 && rect.height > 0;
    };
    const label = (element: Element) => {
      const referenced = element.getAttribute("aria-labelledby")
        ?.split(/\s+/u)
        .map((id) => document.getElementById(id)?.textContent ?? "")
        .join(" ") ?? "";
      return [element.getAttribute("aria-label"), referenced, element.getAttribute("title"), element.textContent]
        .find((value) => Boolean(value?.trim()))
        ?? "";
    };
    const describe = (element: Element) => `${element.tagName.toLowerCase()}${element.id ? `#${element.id}` : ""}`;
    const ids = Array.from(document.querySelectorAll<HTMLElement>("[id]")).map((element) => element.id);
    const duplicateIds = [...new Set(ids.filter((id, index) => ids.indexOf(id) !== index))];
    const formControlsWithoutNames = Array.from(document.querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>("input:not([type='hidden']), select, textarea"))
      .filter(visible)
      .filter((element) => element.labels?.length === 0 && !label(element).trim())
      .map(describe);
    const interactiveWithoutNames = Array.from(document.querySelectorAll<HTMLElement>("button, a[href]"))
      .filter(visible)
      .filter((element) => !label(element).trim())
      .map(describe);
    const invalidAriaReferences = Array.from(document.querySelectorAll<HTMLElement>("[aria-labelledby], [aria-describedby]"))
      .flatMap((element) => ["aria-labelledby", "aria-describedby"].flatMap((attribute) =>
        (element.getAttribute(attribute) ?? "").split(/\s+/u).filter(Boolean)
          .filter((id) => !document.getElementById(id))
          .map((id) => `${describe(element)} ${attribute}=${id}`)
      ));
    const imagesWithoutAlternatives = Array.from(document.querySelectorAll<HTMLImageElement>("img"))
      .filter(visible)
      .filter((image) => !image.hasAttribute("alt"))
      .map(describe);

    const parseColor = (value: string): [number, number, number, number] | null => {
      const parts = value.match(/[\d.]+/gu)?.map(Number) ?? [];
      return parts.length >= 3 ? [parts[0], parts[1], parts[2], parts[3] ?? 1] : null;
    };
    const background = (element: Element): [number, number, number] => {
      let current: Element | null = element;
      while (current) {
        const color = parseColor(getComputedStyle(current).backgroundColor);
        if (color && color[3] > 0) {
          const alpha = color[3];
          return [
            Math.round(color[0] * alpha + 255 * (1 - alpha)),
            Math.round(color[1] * alpha + 255 * (1 - alpha)),
            Math.round(color[2] * alpha + 255 * (1 - alpha))
          ];
        }
        current = current.parentElement;
      }
      return [255, 255, 255];
    };
    const luminance = ([red, green, blue]: readonly number[]) => {
      const linear = [red, green, blue].map((value) => {
        const channel = value / 255;
        return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
      });
      return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
    };
    const contrast = (foreground: readonly number[], backgroundColor: readonly number[]) => {
      const lighter = Math.max(luminance(foreground), luminance(backgroundColor));
      const darker = Math.min(luminance(foreground), luminance(backgroundColor));
      return (lighter + 0.05) / (darker + 0.05);
    };
    const textElements = Array.from(document.querySelectorAll<HTMLElement>("h1, h2, h3, p, label, a[href], button:not([disabled]), dt, dd, th, td, .channel-status, .channel-metric span, .channel-metric strong, .channel-metric small"));
    const contrastFailures = textElements
      .filter(visible)
      .filter((element) => (element.textContent ?? "").trim().length > 0)
      .flatMap((element) => {
        const style = getComputedStyle(element);
        const foreground = parseColor(style.color);
        if (!foreground) return [];
        const size = Number.parseFloat(style.fontSize);
        const weight = Number.parseInt(style.fontWeight, 10) || (style.fontWeight === "bold" ? 700 : 400);
        const threshold = size >= 24 || (size >= 18.66 && weight >= 700) ? 3 : 4.5;
        const ratio = contrast(foreground.slice(0, 3), background(element));
        return ratio + 0.01 < threshold ? [`${describe(element)} ${ratio.toFixed(2)}:${threshold}`] : [];
      });

    return {
      duplicateIds,
      formControlsWithoutNames,
      interactiveWithoutNames,
      invalidAriaReferences,
      imagesWithoutAlternatives,
      contrastFailures,
      mainCount: document.querySelectorAll("main").length,
      h1Count: document.querySelectorAll("h1").length,
      lang: document.documentElement.lang
    };
  });
}

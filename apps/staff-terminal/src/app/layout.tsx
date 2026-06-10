import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import "../components/integrated-terminal.css";

export const metadata: Metadata = {
  title: "Banking Lab Integrated Terminal",
  description: "Synthetic iWorks integrated staff terminal"
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}

import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Banking Lab Customer Web",
  description: "Synthetic customer web banking migration scaffold"
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}

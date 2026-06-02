import "./globals.css";

export const metadata = {
  title: "Banking Lab Staff Terminal",
  description: "Manifest-rendered synthetic staff terminal shell"
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}

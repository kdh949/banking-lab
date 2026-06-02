import "./globals.css";

export const metadata = {
  title: "Banking Lab Audit Console",
  description: "Manifest-rendered synthetic audit console shell"
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}

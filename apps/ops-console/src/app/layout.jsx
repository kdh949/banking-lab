import "./globals.css";

export const metadata = {
  title: "Banking Lab Ops Console",
  description: "Manifest-rendered synthetic operations console shell"
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}

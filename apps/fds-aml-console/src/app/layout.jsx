import "./globals.css";

export const metadata = {
  title: "Banking Lab FDS AML Console",
  description: "Manifest-rendered synthetic FDS and AML console shell"
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}

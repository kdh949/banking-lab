import "./globals.css";

export const metadata = {
  title: "Banking Lab Complaint Portal",
  description: "Manifest-rendered synthetic complaint portal shell"
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}

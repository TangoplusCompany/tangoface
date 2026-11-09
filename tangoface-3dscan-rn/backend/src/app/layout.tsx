import React from 'react';

export const metadata = {
  title: 'TangoFace Backend API Tester',
  description: 'TangoFace 3D Reconstruction Session & Pairing Test Dashboard',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko">
      <body style={{ margin: 0, fontFamily: 'system-ui, -apple-system, sans-serif', backgroundColor: '#090d16', color: '#f8fafc' }}>
        {children}
      </body>
    </html>
  );
}

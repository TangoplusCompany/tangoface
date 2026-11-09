'use client';

import React, { useState } from 'react';

export default function Home() {
  const [sessionId, setSessionId] = useState('');
  const [code, setCode] = useState('');
  const [sessionSecret, setSessionSecret] = useState('');
  const [pairToken, setPairToken] = useState('');
  const [status, setStatus] = useState<any>(null);
  const [logs, setLogs] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);

  const addLog = (msg: string) => {
    setLogs((prev) => [`[${new Date().toLocaleTimeString()}] ${msg}`, ...prev]);
  };

  // 1. 태블릿: 세션 생성
  const handleCreateSession = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/sessions', { method: 'POST' });
      const data = await res.json();
      if (res.ok) {
        setSessionId(data.session_id);
        setCode(data.code);
        setSessionSecret(data.session_secret);
        setStatus('CREATED');
        addLog(`✅ 세션 생성 성공! 6자리 페어링 번호: [ ${data.code} ]`);
      } else {
        addLog(`❌ 세션 생성 실패: ${data.error}`);
      }
    } catch (err: any) {
      addLog(`❌ 오류: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  // 2. 폰: 페어링 번호로 연결
  const handlePair = async () => {
    if (!code) {
      alert('먼저 세션을 생성해주세요.');
      return;
    }
    setLoading(true);
    try {
      const res = await fetch('/api/sessions/pair', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code }),
      });
      const data = await res.json();
      if (res.ok) {
        setPairToken(data.pair_token);
        setStatus('PAIRED');
        addLog(`🎉 페어링 성공! 번호는 일회용으로 즉시 무효화되었습니다. pair_token 발급 완료.`);
      } else {
        addLog(`❌ 페어링 실패: ${data.error}`);
      }
    } catch (err: any) {
      addLog(`❌ 오류: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  // 3. 태블릿: 상태 조회
  const handleCheckStatus = async () => {
    if (!sessionId || !sessionSecret) {
      alert('세션 ID와 session_secret이 필요합니다.');
      return;
    }
    setLoading(true);
    try {
      const res = await fetch(`/api/sessions/${sessionId}/status`, {
        headers: { 'X-Session-Secret': sessionSecret },
      });
      const data = await res.json();
      if (res.ok) {
        setStatus(data.status);
        addLog(`📊 상태 조회 결과: [ ${data.status} ] (세션ID: ${data.session_id.substring(0, 8)}...)`);
      } else {
        addLog(`❌ 상태 조회 실패: ${data.error}`);
      }
    } catch (err: any) {
      addLog(`❌ 오류: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <main style={{ maxWidth: 720, margin: '40px auto', padding: '0 20px' }}>
      <header style={{ borderBottom: '1px solid #1e293b', paddingBottom: 16, marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, margin: 0, color: '#38bdf8' }}>
          TangoFace 3D Backend API Tester
        </h1>
        <p style={{ margin: '8px 0 0 0', color: '#94a3b8', fontSize: 14 }}>
          Next.js(App Router) + Supabase(Postgres/Storage) 세션·페어링 실시간 테스트 대시보드
        </p>
      </header>

      {/* 상태 현황 카드 */}
      <section style={{ backgroundColor: '#0f172a', border: '1px solid #1e293b', borderRadius: 12, padding: 20, marginBottom: 24 }}>
        <h2 style={{ fontSize: 16, margin: '0 0 16px 0', color: '#e2e8f0' }}>현재 세션 상태</h2>
        
        <div style={{ display: 'grid', gridTemplateColumns: '120px 1fr', gap: '8px 12px', fontSize: 13 }}>
          <span style={{ color: '#64748b' }}>세션 ID:</span>
          <span style={{ color: '#f8fafc', wordBreak: 'break-all' }}>{sessionId || '(생성 전)'}</span>

          <span style={{ color: '#64748b' }}>페어링 번호:</span>
          <span style={{ color: code ? '#22c55e' : '#f8fafc', fontWeight: 'bold', fontSize: code ? 18 : 13 }}>
            {code || '(없음)'}
          </span>

          <span style={{ color: '#64748b' }}>진행 상태:</span>
          <span style={{ color: status === 'PAIRED' ? '#38bdf8' : status === 'CREATED' ? '#f59e0b' : '#94a3b8', fontWeight: 'bold' }}>
            {status ? `[ ${status} ]` : '(대기 중)'}
          </span>

          <span style={{ color: '#64748b' }}>pair_token:</span>
          <span style={{ color: '#94a3b8', wordBreak: 'break-all' }}>
            {pairToken ? `${pairToken.substring(0, 24)}... (비공개)` : '(미발급)'}
          </span>
        </div>
      </section>

      {/* 액션 버튼 */}
      <section style={{ display: 'flex', gap: 12, marginBottom: 24 }}>
        <button
          onClick={handleCreateSession}
          disabled={loading}
          style={{ flex: 1, padding: '12px 16px', backgroundColor: '#0284c7', color: '#fff', border: 'none', borderRadius: 8, fontWeight: 'bold', cursor: 'pointer' }}
        >
          1. 세션 생성 (태블릿)
        </button>

        <button
          onClick={handlePair}
          disabled={loading || !code}
          style={{ flex: 1, padding: '12px 16px', backgroundColor: code ? '#16a34a' : '#334155', color: '#fff', border: 'none', borderRadius: 8, fontWeight: 'bold', cursor: code ? 'pointer' : 'not-allowed' }}
        >
          2. 번호 연결 (폰)
        </button>

        <button
          onClick={handleCheckStatus}
          disabled={loading || !sessionId}
          style={{ flex: 1, padding: '12px 16px', backgroundColor: sessionId ? '#475569' : '#334155', color: '#fff', border: 'none', borderRadius: 8, fontWeight: 'bold', cursor: sessionId ? 'pointer' : 'not-allowed' }}
        >
          3. 상태 조회 (태블릿)
        </button>
      </section>

      {/* 실시간 로그 창 */}
      <section style={{ backgroundColor: '#020617', border: '1px solid #1e293b', borderRadius: 8, padding: 16 }}>
        <h3 style={{ margin: '0 0 10px 0', fontSize: 13, color: '#64748b' }}>실시간 API 호출 로그</h3>
        <div style={{ maxHeight: 200, overflowY: 'auto', fontSize: 12, fontFamily: 'monospace', color: '#cbd5e1' }}>
          {logs.length === 0 ? (
            <div style={{ color: '#475569' }}>상단의 [1. 세션 생성] 버튼을 눌러 테스트를 시작하세요.</div>
          ) : (
            logs.map((log, idx) => <div key={idx} style={{ marginBottom: 4 }}>{log}</div>)
          )}
        </div>
      </section>
    </main>
  );
}

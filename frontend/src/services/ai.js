const BASE = import.meta.env.VITE_API_URL || "/api/v1";

export async function sendChatMessage({ message, model, temperature, maxTokens }) {
  const res = await fetch(`${BASE}/ai/chat`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      message,
      model: model || undefined,
      temperature: temperature || undefined,
      maxTokens: maxTokens || undefined,
    }),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data?.message || "Failed to get response from AI");
  }
  return data;
}

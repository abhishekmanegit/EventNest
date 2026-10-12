import { useEffect, useRef, useState } from "react";
import { sendChatMessage } from "../../services/ai";
import { Button, Input } from "../ui";

export default function AiAssistant() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([
    { role: "assistant", content: "Hi! I'm EventNest AI. How can I help you today?" },
  ]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    if (open && inputRef.current) {
      inputRef.current.focus();
    }
  }, [open]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, loading]);

  const handleSend = async (e) => {
    e.preventDefault();
    if (!input.trim() || loading) return;

    const userMessage = { role: "user", content: input.trim() };
    setMessages((prev) => [...prev, userMessage]);
    setInput("");
    setLoading(true);

    try {
      const res = await sendChatMessage({ message: userMessage.content });
      setMessages((prev) => [
        ...prev,
        { role: "assistant", content: res.response || "I couldn't respond. Try again." },
      ]);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        { role: "assistant", content: err.message || "Something went wrong. Please try again." },
      ]);
    } finally {
      setLoading(false);
    }
  };

  const clearChat = () => {
    setMessages([
      { role: "assistant", content: "Hi! I'm EventNest AI. How can I help you today?" },
    ]);
  };

  return (
    <div className="fixed bottom-6 right-6 z-50">
      {!open && (
        <Button
          onClick={() => setOpen(true)}
          className="rounded-full h-14 w-14 p-0 shadow-lg"
          aria-label="Open AI Assistant"
        >
          🤖
        </Button>
      )}

      {open && (
        <div className="w-80 sm:w-96 h-[500px] bg-surface border border-line rounded-ctl shadow-sticky flex flex-col">
          <div className="flex items-center justify-between px-4 py-3 border-b border-line">
            <div className="flex items-center gap-2">
              <span className="text-lg">🤖</span>
              <span className="font-semibold">EventNest AI</span>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={clearChat}
                className="text-xs text-ink-soft hover:text-ink"
                title="Clear chat"
              >
                Clear
              </button>
              <button
                onClick={() => setOpen(false)}
                className="text-ink-soft hover:text-ink"
                aria-label="Close AI Assistant"
              >
                ✕
              </button>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-3 space-y-3">
            {messages.map((msg, idx) => (
              <div
                key={idx}
                className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}
              >
                <div
                  className={`max-w-[85%] px-3 py-2 rounded-ctl text-sm whitespace-pre-wrap ${
                    msg.role === "user"
                      ? "bg-primary text-white"
                      : "bg-canvas text-ink"
                  }`}
                >
                  {msg.content}
                </div>
              </div>
            ))}
            {loading && (
              <div className="flex justify-start">
                <div className="bg-canvas text-ink px-3 py-2 rounded-ctl text-sm">
                  Typing...
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          <form onSubmit={handleSend} className="p-3 border-t border-line flex gap-2">
            <Input
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask me about events, colleges..."
              className="flex-1"
              maxLength={2000}
              disabled={loading}
            />
            <Button type="submit" disabled={loading || !input.trim()}>
              Send
            </Button>
          </form>
        </div>
      )}
    </div>
  );
}

import { Link } from "react-router-dom";
import { Button } from "../components/ui";

export function ForbiddenPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-2 text-center">
      <span className="text-4xl">🚫</span>
      <h1 className="text-2xl font-extrabold">Not your area</h1>
      <p className="max-w-sm text-sm text-ink-soft">You're signed in, but this section needs a different role. Head back to your dashboard.</p>
      <Link to="/" className="mt-4"><Button>Go home</Button></Link>
    </div>
  );
}

export function NotFoundPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-2 text-center">
      <span className="text-4xl">🗺</span>
      <h1 className="text-2xl font-extrabold">This page wandered off</h1>
      <p className="max-w-sm text-sm text-ink-soft">The link may be old, or the event may have been removed.</p>
      <Link to="/" className="mt-4"><Button>Back to home</Button></Link>
    </div>
  );
}
"use strict";
let token = new URLSearchParams(location.hash.slice(1)).get("token");
history.replaceState(null, "", location.pathname);
const form = document.getElementById("reset-form");
const message = document.getElementById("message");
const submit = document.getElementById("submit");
if (!token || !/^[A-Za-z0-9_-]{43}$/.test(token)) {
  form.hidden = true;
  message.textContent = "Link jest nieprawidłowy. Poproś o nowy link w aplikacji.";
}
form.addEventListener("submit", async event => {
  event.preventDefault();
  const password = document.getElementById("password").value;
  if (password !== document.getElementById("confirmation").value) {
    message.textContent = "Hasła muszą być identyczne.";
    return;
  }
  if (new TextEncoder().encode(password).length > 72) {
    message.textContent = "Hasło jest za długie. Użyj krótszego hasła.";
    return;
  }
  submit.disabled = true;
  try {
    const response = await fetch("/auth/password/reset", {
      method: "POST", headers: { "Content-Type": "application/json" },
      credentials: "omit", body: JSON.stringify({ token, password })
    });
    if (response.ok) {
      token = null;
      form.reset();
      form.hidden = true;
      message.textContent = "Hasło zostało zmienione. Wróć do aplikacji i zaloguj się ponownie.";
    } else {
      message.textContent = "Nie udało się zmienić hasła. Link mógł wygasnąć — poproś o nowy w aplikacji.";
    }
  } catch {
    message.textContent = "Brak połączenia. Spróbuj ponownie.";
  } finally {
    submit.disabled = false;
  }
});

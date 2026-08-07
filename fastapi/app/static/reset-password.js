const resetForm = document.querySelector("#reset-password-form");
const csrfCookie = document.cookie.split("; ").find((part) => part.startsWith("csrf_token="));
resetForm.querySelector('[name="csrf_token"]').value = csrfCookie ? decodeURIComponent(csrfCookie.slice(11)) : "";
resetForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const response = await fetch(window.location.pathname, { method: "POST", body: new FormData(resetForm) });
  if (response.ok) { window.location.assign("/#sign-in"); return; }
  const result = await response.json().catch(() => ({}));
  resetForm.querySelector(".form-error").textContent = result.detail || "Password reset failed";
});

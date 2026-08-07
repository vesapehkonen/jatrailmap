const adminStatus = document.querySelector("#admin-status");
const setAdminStatus = (message) => { if (adminStatus) adminStatus.textContent = message; };
const csrfToken = () => {
  const item = document.cookie.split("; ").find((part) => part.startsWith("csrf_token="));
  return item ? decodeURIComponent(item.slice("csrf_token=".length)) : "";
};

async function adminApi(path, method, body) {
  const response = await fetch(path, {
    method,
    headers: { "Content-Type": "application/json", "X-CSRF-Token": csrfToken() },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    const result = await response.json().catch(() => ({}));
    throw new Error(result.detail || "The request failed");
  }
  return response;
}

async function requestAdminPassword() {
  const dialog = document.querySelector("#admin-password-dialog");
  if (!dialog?.showModal) return window.prompt("Confirm your administrator password:") || "";
  const input = dialog.querySelector('[name="admin_password"]');
  input.value = "";
  dialog.showModal();
  input.focus();
  return new Promise((resolve) => dialog.addEventListener("close", () => {
    resolve(dialog.returnValue === "confirm" ? input.value : "");
    input.value = "";
  }, { once: true }));
}

document.querySelectorAll(".admin-quota-form").forEach((form) => {
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const card = form.closest(".admin-user-card");
    const body = Object.fromEntries([...new FormData(form)].filter(([, value]) => value !== ""));
    Object.keys(body).forEach((key) => { body[key] = Number(body[key]); });
    body.admin_password = await requestAdminPassword();
    if (!body.admin_password) return;
    try {
      await adminApi(`/api/v1/admin/users/${card.dataset.userId}/quotas`, "PUT", body);
      setAdminStatus("Quota settings saved.");
    } catch (error) { setAdminStatus(error.message); }
  });
  form.querySelector(".reset-quotas").addEventListener("click", async () => {
    const card = form.closest(".admin-user-card");
    const admin_password = await requestAdminPassword();
    if (!admin_password) return;
    try {
      await adminApi(`/api/v1/admin/users/${card.dataset.userId}/quotas`, "PUT", { admin_password });
      form.reset();
      setAdminStatus("Default quotas restored.");
    } catch (error) { setAdminStatus(error.message); }
  });
});

document.querySelectorAll(".toggle-suspension").forEach((button) => button.addEventListener("click", async () => {
  const card = button.closest(".admin-user-card");
  const suspended = button.dataset.suspended !== "true";
  if (suspended && !await window.confirmAction("The account will be logged out immediately and unable to sign in or upload trails.", "Suspend account?", "Suspend")) return;
  const admin_password = await requestAdminPassword();
  if (!admin_password) return;
  try {
    await adminApi(`/api/v1/admin/users/${card.dataset.userId}/status`, "PATCH", { suspended, admin_password });
    window.location.reload();
  } catch (error) { setAdminStatus(error.message); }
}));

document.querySelectorAll(".delete-user").forEach((button) => button.addEventListener("click", async () => {
  const card = button.closest(".admin-user-card");
  if (!await window.confirmAction("This permanently deletes the account, its trails, GPS records, photos, groups and sessions.", "Delete account?", "Delete everything")) return;
  const admin_password = await requestAdminPassword();
  if (!admin_password) return;
  try {
    await adminApi(`/api/v1/admin/users/${card.dataset.userId}`, "DELETE", { admin_password });
    card.remove();
    setAdminStatus("Account deleted.");
  } catch (error) { setAdminStatus(error.message); }
}));

document.querySelectorAll(".unpublish-form").forEach((form) => form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const card = form.closest("[data-trail-id]");
  const reason = String(new FormData(form).get("reason") || "").trim();
  if (!await window.confirmAction("The trail will become private and its owner will receive your reason.", "Make this trail private?", "Make private")) return;
  const admin_password = await requestAdminPassword();
  if (!admin_password) return;
  try {
    await adminApi(`/api/v1/admin/trails/${card.dataset.trailId}/unpublish`, "POST", { reason, admin_password });
    card.remove();
    setAdminStatus("Trail made private and owner notified.");
  } catch (error) { setAdminStatus(error.message); }
}));

document.querySelectorAll(".approve-user").forEach((button) => button.addEventListener("click", async () => {
  const card = button.closest(".admin-user-card");
  const admin_password = await requestAdminPassword();
  if (!admin_password) return;
  try { await adminApi(`/api/v1/admin/users/${card.dataset.userId}/approval`, "PATCH", { approved: true, admin_password }); window.location.reload(); }
  catch (error) { setAdminStatus(error.message); }
}));

document.querySelectorAll(".toggle-role").forEach((button) => button.addEventListener("click", async () => {
  const card = button.closest(".admin-user-card");
  const role = button.dataset.role === "admin" ? "user" : "admin";
  if (!await window.confirmAction(`This account will become ${role === "admin" ? "an administrator" : "a regular user"}.`, "Change administrator role?", "Change role")) return;
  const admin_password = await requestAdminPassword();
  if (!admin_password) return;
  try { await adminApi(`/api/v1/admin/users/${card.dataset.userId}/role`, "PATCH", { role, admin_password }); window.location.reload(); }
  catch (error) { setAdminStatus(error.message); }
}));

document.querySelector("#registration-settings-form")?.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const values = Object.fromEntries(new FormData(form));
  const body = {
    enabled: form.elements.enabled.checked,
    approval_required: form.elements.approval_required.checked,
    account_storage_mb: Number(values.account_storage_mb), image_mb: Number(values.image_mb),
    photos_per_trail: Number(values.photos_per_trail), upload_mb: Number(values.upload_mb),
    admin_password: await requestAdminPassword(),
  };
  if (!body.admin_password) return;
  try { await adminApi("/api/v1/admin/settings/registration", "PUT", body); setAdminStatus("Registration settings saved."); }
  catch (error) { setAdminStatus(error.message); }
});

document.querySelector(".issue-password-reset")?.addEventListener("click", async (event) => {
  const container = event.currentTarget.closest("[data-user-id]");
  const admin_password = await requestAdminPassword();
  if (!admin_password) return;
  try {
    const response = await adminApi(`/api/v1/admin/users/${container.dataset.userId}/password-reset`, "POST", { admin_password });
    const result = await response.json();
    const output = document.querySelector("#reset-link-output");
    output.innerHTML = "";
    const input = document.createElement("input"); input.readOnly = true; input.value = result.reset_url; input.setAttribute("aria-label", "One-time password reset link"); output.append(input);
  } catch (error) { setAdminStatus(error.message); }
});

document.querySelector("#cleanup-orphans")?.addEventListener("click", async () => {
  if (!await window.confirmAction("Only orphan child records are removed. Trails are never automatically deleted.", "Remove orphan records?", "Clean up")) return;
  const admin_password = await requestAdminPassword();
  if (!admin_password) return;
  try { await adminApi("/api/v1/admin/maintenance/cleanup", "POST", { admin_password }); window.location.reload(); }
  catch (error) { setAdminStatus(error.message); }
});

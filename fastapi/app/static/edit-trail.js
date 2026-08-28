const editor = document.querySelector(".editor-page");

if (editor && window.L) {
  const trailId = editor.dataset.trailId;
  const trailAccess = editor.dataset.trailAccess || "private";
  const detailsForm = document.querySelector("#trail-details");
  const permissionForm = document.querySelector("#trail-permissions");
  const photoForm = document.querySelector("#photo-form");
  const photoPanel = document.querySelector("#photo-side-panel");
  const saveChanges = document.querySelector("#save-trail-changes");
  const cancelChanges = document.querySelector("#cancel-trail-changes");
  const unsavedIndicator = document.querySelector("#unsaved-indicator");
  const undoButton = document.querySelector("#undo-map-action");
  const toastRegion = document.querySelector("#toast-region");
  const mainPhotoControl = photoForm.elements.main_photo;
  let locationStates = [];
  let photoStates = [];
  let selectedPhoto = null;
  let actionHistory = [];
  let redrawPath = () => {};
  let dirty = false;
  let originalMainPictureId = editor.dataset.mainPictureId || "";
  let draftMainPictureId = originalMainPictureId;

  const locationIcon = L.divIcon({
    className: "location-point-icon",
    iconSize: [12, 12],
    iconAnchor: [6, 6],
    popupAnchor: [0, -7],
  });
  function photoThumbnailIcon(imageId) {
    const source = `/image/${encodeURIComponent(imageId)}`;
    return L.divIcon({
      className: "photo-thumbnail-marker",
      html: `<span style="background-image: url('${source}')"></span>`,
      iconSize: [46, 46],
      iconAnchor: [23, 23],
    });
  }

  const csrfToken = () => {
    const item = document.cookie.split("; ").find((part) => part.startsWith("csrf_token="));
    return item ? decodeURIComponent(item.slice("csrf_token=".length)) : "";
  };
  async function api(path, method, body) {
    const response = await fetch(path, {
      method,
      headers: { "Content-Type": "application/json", "X-CSRF-Token": csrfToken() },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) {
      const result = await response.json().catch(() => ({}));
      throw new Error(result.detail || "The change could not be saved");
    }
    return response.status === 204 ? null : response.json();
  }

  function toast(message, error = false) {
    const node = document.createElement("div");
    node.className = `toast${error ? " is-error" : ""}`;
    node.textContent = message;
    toastRegion.append(node);
    window.setTimeout(() => node.remove(), 4200);
  }

  function formSnapshot(form) {
    return JSON.stringify(Array.from(new FormData(form).entries()));
  }
  function restoreForm(form, snapshot) {
    const entries = JSON.parse(snapshot);
    const values = new Map(entries);
    form.querySelectorAll("input, textarea, select").forEach((control) => {
      if (control.type === "checkbox") {
        control.checked = entries.some(([name, value]) => name === control.name && value === control.value);
      } else if (values.has(control.name)) {
        control.value = values.get(control.name);
      }
    });
  }
  let originalDetails = formSnapshot(detailsForm);
  let originalPermissions = formSnapshot(permissionForm);

  function positionChanged(state) {
    const position = state.marker.getLatLng();
    return position.lat !== state.originalPosition.lat || position.lng !== state.originalPosition.lng;
  }
  function photoMetadataChanged(state) {
    return JSON.stringify(state.draft) !== JSON.stringify(state.originalMetadata);
  }
  function calculateDirty() {
    return formSnapshot(detailsForm) !== originalDetails
      || formSnapshot(permissionForm) !== originalPermissions
      || draftMainPictureId !== originalMainPictureId
      || locationStates.some((state) => state.deleted || positionChanged(state))
      || photoStates.some((state) => state.deleted || positionChanged(state) || photoMetadataChanged(state));
  }
  function updateMarkerStyles() {
    locationStates.forEach((state) => {
      const element = state.marker.getElement();
      element?.classList.toggle("is-modified", positionChanged(state));
      element?.classList.toggle("is-pending-removal", state.deleted);
    });
    photoStates.forEach((state) => {
      const element = state.marker.getElement();
      element?.classList.toggle("is-modified", positionChanged(state) || photoMetadataChanged(state));
      element?.classList.toggle("is-pending-removal", state.deleted);
      element?.classList.toggle("is-selected", state === selectedPhoto);
      element?.classList.toggle("is-main-photo", state.picture.id === draftMainPictureId);
    });
  }
  function updateDirtyState() {
    dirty = calculateDirty();
    saveChanges.disabled = !dirty;
    cancelChanges.disabled = !dirty;
    unsavedIndicator.classList.toggle("is-dirty", dirty);
    unsavedIndicator.querySelector("span").textContent = dirty ? "Unsaved changes" : "All changes saved";
    undoButton.disabled = actionHistory.length === 0;
    updateMarkerStyles();
  }

  document.querySelectorAll(".editor-tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      document.querySelectorAll(".editor-tab").forEach((item) => {
        const active = item === tab;
        item.classList.toggle("is-active", active);
        item.setAttribute("aria-selected", String(active));
      });
      document.querySelectorAll(".editor-section").forEach((section) => {
        const active = section.dataset.section === tab.dataset.tab;
        section.hidden = !active;
        section.classList.toggle("is-active", active);
      });
      if (tab.dataset.tab === "map") window.setTimeout(() => map.invalidateSize(), 0);
    });
  });
  function openMapTab() {
    document.querySelector('.editor-tab[data-tab="map"]')?.click();
  }

  [detailsForm, permissionForm].forEach((form) => {
    form.addEventListener("submit", (event) => event.preventDefault());
    form.addEventListener("input", updateDirtyState);
    form.addEventListener("change", updateDirtyState);
  });

  function readPhotoDraft() {
    if (!selectedPhoto) return;
    const values = new FormData(photoForm);
    selectedPhoto.draft = {
      picturename: String(values.get("picturename") || ""),
      description: String(values.get("description") || ""),
      access: String(values.get("access") || "private"),
      groups: values.getAll("groups").map(String),
    };
  }
  function showPhoto(state) {
    if (selectedPhoto) readPhotoDraft();
    selectedPhoto = state;
    photoPanel.hidden = false;
    document.querySelector("#photo-original-link").href = `/image/${state.picture.imageid}`;
    const image = document.querySelector("#photo-panel-image");
    image.src = `/image/${state.picture.imageid}`;
    image.alt = state.draft.picturename || "Trail photo";
    photoForm.elements.picturename.value = state.draft.picturename;
    photoForm.elements.description.value = state.draft.description;
    photoForm.elements.access.value = state.draft.access;
    photoForm.querySelectorAll('input[name="groups"]').forEach((input) => {
      input.checked = state.draft.groups.includes(input.value);
    });
    mainPhotoControl.checked = state.picture.id === draftMainPictureId;
    updateDirtyState();
  }
  function closePhotoPanel(persist = true) {
    if (persist) readPhotoDraft();
    selectedPhoto = null;
    photoPanel.hidden = true;
    updateDirtyState();
  }
  photoForm.addEventListener("submit", (event) => event.preventDefault());
  photoForm.addEventListener("input", () => { readPhotoDraft(); updateDirtyState(); });
  photoForm.addEventListener("change", () => { readPhotoDraft(); updateDirtyState(); });
  mainPhotoControl.addEventListener("change", () => {
    if (!selectedPhoto) return;
    draftMainPictureId = mainPhotoControl.checked ? selectedPhoto.picture.id : "";
    updateDirtyState();
  });
  document.querySelector("#close-photo-panel").addEventListener("click", closePhotoPanel);

  const map = L.map("map").setView([0, 0], 2);
  window.addMapFullscreenControl?.(map, document.querySelector(".editor-map-stage"));
  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap contributors",
  }).addTo(map);

  function pushAction(action) {
    actionHistory.push(action);
    if (actionHistory.length > 30) actionHistory.shift();
    updateDirtyState();
  }
  undoButton.addEventListener("click", () => {
    const action = actionHistory.pop();
    if (!action) return;
    if (action.type === "move") action.state.marker.setLatLng(action.before);
    if (action.type === "remove") {
      action.state.deleted = false;
      action.state.marker.dragging?.enable();
      action.state.marker.setOpacity(1);
      if (action.mainPictureBefore !== undefined) {
        draftMainPictureId = action.mainPictureBefore;
      }
    }
    redrawPath();
    updateDirtyState();
    toast("Last map action undone.");
  });

  fetch(`/api/v1/trails/${trailId}/track`)
    .then((response) => {
      if (!response.ok) throw new Error("Trail data is unavailable");
      return response.json();
    })
    .then((data) => {
      const points = data.locs.map((item) => [item.loc.coordinates[1], item.loc.coordinates[0]]);
      const line = L.polyline(points, { color: "#176b4d", weight: 4 }).addTo(map);
      if (points.length) map.fitBounds(line.getBounds(), { padding: [24, 24] });
      redrawPath = () => line.setLatLngs(
        locationStates.filter((state) => !state.deleted).map((state) => state.marker.getLatLng()),
      );

      data.locs.forEach((location) => {
        const marker = L.marker(
          [location.loc.coordinates[1], location.loc.coordinates[0]],
          { draggable: true, title: "GPS point", icon: locationIcon },
        ).addTo(map);
        const initial = marker.getLatLng();
        const state = {
          location,
          marker,
          originalPosition: { lat: initial.lat, lng: initial.lng },
          deleted: false,
          dragStart: null,
        };
        locationStates.push(state);
        marker.on("dragstart", () => { state.dragStart = marker.getLatLng(); });
        marker.on("drag", redrawPath);
        marker.on("dragend", () => {
          redrawPath();
          pushAction({ type: "move", state, before: state.dragStart });
          toast("GPS point moved. Save to apply.");
        });
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "danger";
        remove.textContent = "Mark point for removal";
        remove.addEventListener("click", () => {
          state.deleted = true;
          marker.dragging?.disable();
          marker.setOpacity(.28);
          marker.closePopup();
          redrawPath();
          pushAction({ type: "remove", state });
          toast("GPS point marked for removal.");
        });
        marker.bindPopup(remove);
      });

      data.pics.forEach((picture) => {
        const marker = L.marker(
          [picture.loc.coordinates[1], picture.loc.coordinates[0]],
          { draggable: true, title: picture.picturename || "Trail photo", icon: photoThumbnailIcon(picture.imageid) },
        ).addTo(map);
        const initial = marker.getLatLng();
        const metadata = {
          picturename: picture.picturename || "",
          description: picture.description || "",
          access: picture.access || trailAccess,
          groups: (picture.groups || []).map(String),
        };
        const state = {
          picture,
          marker,
          originalPosition: { lat: initial.lat, lng: initial.lng },
          originalMetadata: structuredClone(metadata),
          draft: structuredClone(metadata),
          deleted: false,
          dragStart: null,
        };
        photoStates.push(state);
        marker.on("click", () => { openMapTab(); showPhoto(state); });
        marker.on("dragstart", () => { state.dragStart = marker.getLatLng(); });
        marker.on("dragend", () => {
          pushAction({ type: "move", state, before: state.dragStart });
          toast("Photo moved. Save to apply.");
        });
      });
      updateDirtyState();
    })
    .catch((error) => toast(error.message, true));

  document.querySelector("#delete-photo").addEventListener("click", async () => {
    if (!selectedPhoto || !await window.confirmAction(
      "The photo will be removed when you save the trail changes.",
      "Remove this photo?",
      "Remove photo",
    )) return;
    const state = selectedPhoto;
    const mainPictureBefore = draftMainPictureId;
    if (state.picture.id === draftMainPictureId) draftMainPictureId = "";
    state.deleted = true;
    state.marker.dragging?.disable();
    state.marker.setOpacity(.28);
    closePhotoPanel();
    pushAction({ type: "remove", state, mainPictureBefore });
    toast("Photo marked for removal.");
  });

  saveChanges.addEventListener("click", async () => {
    readPhotoDraft();
    if (!detailsForm.reportValidity()) return;
    const details = new FormData(detailsForm);
    const permissions = new FormData(permissionForm);
    const changedLocations = locationStates.filter((state) => state.deleted || positionChanged(state));
    const changedPhotos = photoStates.filter(
      (state) => state.deleted || positionChanged(state) || photoMetadataChanged(state),
    );
    saveChanges.disabled = true;
    cancelChanges.disabled = true;
    try {
      await api(`/api/v1/trails/${trailId}`, "PATCH", {
        trailname: details.get("trailname"),
        location: details.get("location"),
        description: details.get("description"),
        main_picture_id: draftMainPictureId,
      });
      await api(`/api/v1/trails/${trailId}/permissions`, "PUT", {
        access: permissions.get("access"),
        groups: permissions.getAll("groups"),
      });
      await Promise.all(changedLocations.map((state) => {
        if (state.deleted) return api(`/api/v1/trails/${trailId}/locations/${state.location.id}`, "DELETE");
        const position = state.marker.getLatLng();
        return api(`/api/v1/trails/${trailId}/locations/${state.location.id}`, "PATCH", {
          longitude: position.lng, latitude: position.lat,
        });
      }));
      await Promise.all(changedPhotos.map(async (state) => {
        if (state.deleted) return api(`/api/v1/trails/${trailId}/pictures/${state.picture.id}`, "DELETE");
        const position = state.marker.getLatLng();
        await api(`/api/v1/trails/${trailId}/pictures/${state.picture.id}`, "PATCH", {
          picturename: state.draft.picturename,
          description: state.draft.description,
          longitude: position.lng,
          latitude: position.lat,
        });
        return api(`/api/v1/trails/${trailId}/pictures/${state.picture.id}/permissions`, "PUT", {
          access: state.draft.access,
          groups: state.draft.groups,
        });
      }));
      locationStates.filter((state) => !state.deleted).forEach((state) => {
        const position = state.marker.getLatLng();
        state.originalPosition = { lat: position.lat, lng: position.lng };
      });
      photoStates.filter((state) => !state.deleted).forEach((state) => {
        const position = state.marker.getLatLng();
        state.originalPosition = { lat: position.lat, lng: position.lng };
        state.originalMetadata = structuredClone(state.draft);
      });
      locationStates.filter((state) => state.deleted).forEach((state) => state.marker.remove());
      photoStates.filter((state) => state.deleted).forEach((state) => state.marker.remove());
      locationStates = locationStates.filter((state) => !state.deleted);
      photoStates = photoStates.filter((state) => !state.deleted);
      originalMainPictureId = draftMainPictureId;
      editor.dataset.mainPictureId = originalMainPictureId;
      originalDetails = formSnapshot(detailsForm);
      originalPermissions = formSnapshot(permissionForm);
      actionHistory = [];
      closePhotoPanel(false);
      redrawPath();
      toast("Trail changes saved.");
    } catch (error) {
      toast(`${error.message}. Reload before making further changes.`, true);
    } finally {
      updateDirtyState();
    }
  });

  cancelChanges.addEventListener("click", () => {
    restoreForm(detailsForm, originalDetails);
    restoreForm(permissionForm, originalPermissions);
    draftMainPictureId = originalMainPictureId;
    locationStates.forEach((state) => {
      state.deleted = false;
      state.marker.setLatLng(state.originalPosition);
      state.marker.setOpacity(1);
      state.marker.dragging?.enable();
    });
    photoStates.forEach((state) => {
      state.deleted = false;
      state.draft = structuredClone(state.originalMetadata);
      state.marker.setLatLng(state.originalPosition);
      state.marker.setOpacity(1);
      state.marker.dragging?.enable();
    });
    actionHistory = [];
    closePhotoPanel(false);
    redrawPath();
    updateDirtyState();
    toast("Unsaved changes canceled.");
  });

  document.querySelector("#delete-trail").addEventListener("click", async () => {
    if (!await window.confirmAction(
      "This permanently removes the trail, GPS points, photos, and stored images.",
      "Delete this trail?",
      "Delete trail",
    )) return;
    try {
      await api(`/api/v1/trails/${trailId}`, "DELETE");
      dirty = false;
      window.location.assign("/");
    } catch (error) { toast(error.message, true); }
  });

  window.addEventListener("beforeunload", (event) => {
    if (!dirty) return;
    event.preventDefault();
    event.returnValue = "";
  });
}

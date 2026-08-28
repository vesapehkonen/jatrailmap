const container = document.querySelector(".trail-workspace");
if (container && window.L) {
  function milesBetween(first, second) {
    const radians = (degrees) => degrees * Math.PI / 180;
    const lat1 = radians(first[1]);
    const lat2 = radians(second[1]);
    const deltaLat = lat2 - lat1;
    const deltaLon = radians(second[0] - first[0]);
    const value = Math.sin(deltaLat / 2) ** 2
      + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) ** 2;
    return 3958.7613 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
  }

  function renderElevation(locations) {
    const chart = document.querySelector("#elevation-chart");
    if (!chart) return;
    const profile = [];
    let distance = 0;
    let previous = null;
    locations.forEach((location) => {
      const point = location.loc?.coordinates;
      if (!Array.isArray(point) || point.length < 2) return;
      if (previous) distance += milesBetween(previous, point);
      previous = point;
      const meters = Number(point[2]);
      if (point.length >= 3 && Number.isFinite(meters)) {
        profile.push({
          distance,
          elevation: meters * 3.28084,
          latitude: Number(point[1]),
          longitude: Number(point[0]),
        });
      }
    });
    if (profile.length < 2) return;

    const namespace = "http://www.w3.org/2000/svg";
    const width = 360;
    const height = 240;
    const margin = { left: 52, right: 12, top: 24, bottom: 38 };
    const minElevation = Math.min(...profile.map((point) => point.elevation));
    const maxElevation = Math.max(...profile.map((point) => point.elevation));
    const elevationRange = Math.max(1, maxElevation - minElevation);
    const maxDistance = Math.max(0.001, profile[profile.length - 1].distance);
    const x = (value) => margin.left + value / maxDistance * (width - margin.left - margin.right);
    const y = (value) => height - margin.bottom
      - (value - minElevation) / elevationRange * (height - margin.top - margin.bottom);

    function element(name, attributes = {}) {
      const node = document.createElementNS(namespace, name);
      Object.entries(attributes).forEach(([key, value]) => node.setAttribute(key, value));
      return node;
    }

    const baseline = height - margin.bottom;
    const definitions = element("defs");
    const gradient = element("linearGradient", {
      id: "elevation-fill",
      x1: "0",
      y1: "0",
      x2: "0",
      y2: "1",
    });
    gradient.append(
      element("stop", { offset: "0%", "stop-color": "#23815e", "stop-opacity": ".42" }),
      element("stop", { offset: "100%", "stop-color": "#23815e", "stop-opacity": ".04" }),
    );
    definitions.append(gradient);
    chart.append(definitions);
    chart.append(element("line", {
      x1: margin.left,
      y1: (margin.top + baseline) / 2,
      x2: width - margin.right,
      y2: (margin.top + baseline) / 2,
      class: "chart-grid-line",
    }));
    chart.append(element("line", {
      x1: margin.left, y1: baseline, x2: width - margin.right, y2: baseline, class: "chart-axis",
    }));
    chart.append(element("line", {
      x1: margin.left, y1: margin.top, x2: margin.left, y2: baseline, class: "chart-axis",
    }));
    const areaPoints = [
      `${x(0)},${baseline}`,
      ...profile.map((point) => `${x(point.distance)},${y(point.elevation)}`),
      `${x(maxDistance)},${baseline}`,
    ].join(" ");
    chart.append(element("polygon", {
      points: areaPoints,
      class: "elevation-area",
      fill: "url(#elevation-fill)",
    }));
    chart.append(element("polyline", {
      points: profile.map((point) => `${x(point.distance)},${y(point.elevation)}`).join(" "),
      class: "elevation-line",
    }));

    const hoverLine = element("line", {
      y1: margin.top,
      y2: baseline,
      class: "elevation-hover-line",
      visibility: "hidden",
    });
    const hoverPoint = element("circle", {
      r: 5,
      class: "elevation-hover-point",
      visibility: "hidden",
    });
    const hoverLabel = element("text", {
      y: 15,
      class: "elevation-hover-label",
      visibility: "hidden",
    });
    chart.append(hoverLine, hoverPoint, hoverLabel);

    [
      { x: margin.left, y: baseline + 24, text: "0 mi", anchor: "start" },
      { x: width - margin.right, y: baseline + 24, text: `${maxDistance.toFixed(1)} mi`, anchor: "end" },
      { x: margin.left - 8, y: y(maxElevation) + 4, text: `${Math.round(maxElevation)} ft`, anchor: "end" },
      { x: margin.left - 8, y: y(minElevation) + 4, text: `${Math.round(minElevation)} ft`, anchor: "end" },
    ].forEach((label) => {
      const text = element("text", { x: label.x, y: label.y, "text-anchor": label.anchor });
      text.textContent = label.text;
      chart.append(text);
    });

    const interaction = element("rect", {
      x: margin.left,
      y: margin.top,
      width: width - margin.left - margin.right,
      height: baseline - margin.top,
      class: "elevation-interaction",
    });
    chart.append(interaction);
    let mapPosition = null;
    interaction.addEventListener("mousemove", (event) => {
      const bounds = chart.getBoundingClientRect();
      const svgX = (event.clientX - bounds.left) / bounds.width * width;
      const hoveredDistance = Math.max(
        0,
        Math.min(maxDistance, (svgX - margin.left) / (width - margin.left - margin.right) * maxDistance),
      );
      const selected = profile.reduce((nearest, point) => (
        Math.abs(point.distance - hoveredDistance) < Math.abs(nearest.distance - hoveredDistance)
          ? point : nearest
      ));
      const selectedX = x(selected.distance);
      const selectedY = y(selected.elevation);
      hoverLine.setAttribute("x1", selectedX);
      hoverLine.setAttribute("x2", selectedX);
      hoverPoint.setAttribute("cx", selectedX);
      hoverPoint.setAttribute("cy", selectedY);
      hoverLabel.setAttribute("x", Math.min(width - 108, Math.max(margin.left, selectedX - 48)));
      hoverLabel.textContent = `${selected.distance.toFixed(1)} mi · ${Math.round(selected.elevation)} ft`;
      [hoverLine, hoverPoint, hoverLabel].forEach((node) => node.setAttribute("visibility", "visible"));
      if (mapPosition) mapPosition.remove();
      mapPosition = L.circleMarker([selected.latitude, selected.longitude], {
        radius: 9,
        color: "#fff",
        weight: 3,
        fillColor: "#e7a321",
        fillOpacity: 1,
      }).addTo(map);
      mapPosition.bindTooltip(
        `${selected.distance.toFixed(1)} mi · ${Math.round(selected.elevation)} ft`,
        { permanent: true, direction: "top", className: "endpoint-label" },
      );
    });
    interaction.addEventListener("mouseleave", () => {
      [hoverLine, hoverPoint, hoverLabel].forEach((node) => node.setAttribute("visibility", "hidden"));
      if (mapPosition) {
        mapPosition.remove();
        mapPosition = null;
      }
    });
  }

  function roundedPath(points, iterations = 2) {
    let result = points;
    for (let pass = 0; pass < iterations && result.length > 2; pass += 1) {
      const next = [result[0]];
      for (let index = 0; index < result.length - 1; index += 1) {
        const current = result[index];
        const following = result[index + 1];
        next.push([
          current[0] * 0.75 + following[0] * 0.25,
          current[1] * 0.75 + following[1] * 0.25,
        ]);
        next.push([
          current[0] * 0.25 + following[0] * 0.75,
          current[1] * 0.25 + following[1] * 0.75,
        ]);
      }
      next.push(result[result.length - 1]);
      result = next;
    }
    return result;
  }

  function photoThumbnailIcon(imageId) {
    const source = `/image/${encodeURIComponent(imageId)}`;
    return L.divIcon({
      className: "photo-thumbnail-marker",
      html: `<span style="background-image: url('${source}')"></span>`,
      iconSize: [42, 42],
      iconAnchor: [21, 21],
      popupAnchor: [0, -23],
    });
  }
  function endpointIcon(kind) {
    const start = kind === "start";
    return L.divIcon({
      className: `endpoint-marker endpoint-${kind}`,
      html: `<span>${start ? "Start" : "Finish"}</span>`,
      iconSize: [76, 28],
      iconAnchor: start ? [10, 14] : [66, 14],
    });
  }
  const map = L.map("map").setView([0, 0], 2);
  window.addMapFullscreenControl?.(map, document.querySelector(".map-shell"));
  const mapStatus = document.querySelector("#map-status");
  const photoMarkers = [];
  const spiderLegs = L.layerGroup().addTo(map);
  let spiderfied = [];

  function collapsePhotos() {
    spiderfied.forEach((record) => {
      record.marker.setLatLng(record.original);
      record.marker.setZIndexOffset(0);
      record.marker.getElement()?.classList.remove("is-spiderfied");
    });
    spiderfied = [];
    spiderLegs.clearLayers();
  }

  function nearbyPhotos(selected) {
    const selectedPoint = map.latLngToLayerPoint(selected.original);
    return photoMarkers.filter((record) => (
      map.latLngToLayerPoint(record.original).distanceTo(selectedPoint) <= 52
    ));
  }

  function spiderfyPhotos(selected) {
    const nearby = nearbyPhotos(selected);
    if (nearby.length < 2 || spiderfied.includes(selected)) return false;
    collapsePhotos();
    const center = map.latLngToLayerPoint(selected.original);
    nearby.forEach((record, index) => {
      const ring = Math.floor(index / 10);
      const firstInRing = ring * 10;
      const itemsInRing = Math.min(10, nearby.length - firstInRing);
      const angle = Math.PI * 2 * (index - firstInRing) / itemsInRing - Math.PI / 2;
      const radius = 58 + ring * 44;
      const displayPoint = L.point(
        center.x + Math.cos(angle) * radius,
        center.y + Math.sin(angle) * radius,
      );
      const displayLocation = map.layerPointToLatLng(displayPoint);
      record.marker.setLatLng(displayLocation);
      record.marker.setZIndexOffset(1000 + index);
      record.marker.getElement()?.classList.add("is-spiderfied");
      L.polyline([record.original, displayLocation], {
        className: "photo-spider-leg",
        color: "#526159",
        weight: 1.5,
        opacity: .65,
        interactive: false,
      }).addTo(spiderLegs);
    });
    spiderfied = nearby;
    return true;
  }

  map.on("click zoomstart dragstart", collapsePhotos);
  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    attribution: "&copy; OpenStreetMap contributors",
  }).addTo(map);

  fetch(`/api/v1/trails/${container.dataset.trailId}/track`)
    .then((response) => {
      if (!response.ok) throw new Error("Trail data is unavailable");
      return response.json();
    })
    .then((data) => {
      const points = data.locs.map((item) => [item.loc.coordinates[1], item.loc.coordinates[0]]);
      if (points.length) {
        const line = L.polyline(roundedPath(points), {
          color: "#176b4d",
          weight: 4,
          className: "view-trail-line",
          smoothFactor: 1,
        }).addTo(map);
        map.fitBounds(line.getBounds(), { padding: [24, 24] });
        L.marker(points[0], { icon: endpointIcon("start"), interactive: false }).addTo(map);
        L.marker(points[points.length - 1], {
          icon: endpointIcon("finish"), interactive: false,
        }).addTo(map);
      } else {
        mapStatus.textContent = "This trail has no GPS points.";
      }
      data.pics.forEach((picture) => {
        const point = [picture.loc.coordinates[1], picture.loc.coordinates[0]];
        const title = document.createElement("strong");
        title.textContent = picture.picturename || "Trail photo";
        const image = document.createElement("img");
        image.src = `/image/${picture.imageid}`;
        image.alt = picture.picturename || "Trail photo";
        image.className = "map-photo";
        const imageLink = document.createElement("a");
        imageLink.href = `/image/${picture.imageid}`;
        imageLink.target = "_blank";
        imageLink.rel = "noopener";
        imageLink.title = "Open original photo";
        imageLink.append(image);
        const popupContent = document.createElement("div");
        popupContent.className = "photo-popup-content";
        popupContent.append(title, document.createElement("br"), imageLink);
        if (picture.description) {
          const description = document.createElement("p");
          description.className = "photo-popup-description";
          description.textContent = picture.description;
          popupContent.append(description);
        }
        const marker = L.marker(point, {
          icon: photoThumbnailIcon(picture.imageid),
          title: picture.picturename || "Trail photo",
          bubblingMouseEvents: false,
        }).addTo(map);
        const photoPopup = L.popup({ minWidth: 320, maxWidth: 360 }).setContent(popupContent);
        const record = { marker, original: L.latLng(point) };
        photoMarkers.push(record);
        marker.on("click", (event) => {
          if (event.originalEvent) L.DomEvent.stopPropagation(event.originalEvent);
          if (spiderfyPhotos(record)) return;
          document.querySelectorAll(".photo-thumbnail-marker.is-selected").forEach(
            (element) => element.classList.remove("is-selected"),
          );
          marker.getElement()?.classList.add("is-selected");
          photoPopup.setLatLng(marker.getLatLng()).openOn(map);
        });
        photoPopup.on("remove", () => marker.getElement()?.classList.remove("is-selected"));
      });
      renderElevation(data.locs);
      if (points.length) mapStatus.classList.add("is-hidden");
    })
    .catch((error) => {
      mapStatus.textContent = error.message;
      mapStatus.classList.add("is-error");
    });
}

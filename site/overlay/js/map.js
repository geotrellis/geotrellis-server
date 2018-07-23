// GLOBAL STATE/MANAGEMENT FUNCS
var cogForms = [];
var MAX_COGS = 5;
var BASE_URL = "http://localhost:9000/maml/overlay/"
var APP_ID;
var overlayLayer;

function addCog(cog) {
  cogForms.push(cog)
  $("#sidebody").append(cog.dom)
}
function removeCog(id) {
  cogForms = _.filter(cogForms, function(cf) { return cf.id !== id })
  $("#" + id).remove()
}

// debugging helper
function printForms(cforms) {
  _.each(cforms, function(cf) {
    var cog = cf.getCog();
    var weight = cf.getWeight();
    console.log("ID", cf.id, "URI", cog.uri, "BAND", cog.band, "WEIGHT", weight);
  });
}

// for serialization of args to MAML
function paramMap(cforms) {
  var grouped = _.map(cforms, function(cf) { return [cf.id, { band: cf.getCog().band, uri: cf.getCog().uri, weight: cf.getWeight() }] })
  return _.object(grouped)
}


/** Spawn Map */
var map = L.map('map', { center: [0, 0], zoom: 2, zoomControl: false });
var tileLayer = L.tileLayer('http://stamen-tiles-{s}.a.ssl.fastly.net/toner-lite/{z}/{x}/{y}.{ext}', {
  attribution: 'Map tiles by <a href="http://stamen.com">Stamen Design</a>, <a href="http://creativecommons.org/licenses/by/3.0">CC BY 3.0</a> &mdash; Map data &copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  subdomains: 'abcd',
  minZoom: 0,
  maxZoom: 20,
  ext: 'png'
})
var leafletControl = L.control.zoom({ position: 'topright' })
tileLayer.addTo(map);
leafletControl.addTo(map);

// a good-enough guid for keying JS to the dom
function guid() {
  function s4() {
    return Math.floor((1 + Math.random()) * 0x10000)
      .toString(16)
      .substring(1);
  }
  return s4() + s4() + '-' + s4() + '-' + s4() + '-' + s4() + '-' + s4() + s4() + s4();
}

// the definition necessary to realize bytes from some remote geotiff
function Cog(uri, band) {
  return {
    "band": band,
    "uri": uri
  }
}

// Create the URI box/band dropdown element DOM
function UriBox(id, uri, band) {
  var band = band;

  var currentBandLabel = $("<button>", {
    "class": "btn btn-default dropdown-toggle",
    "type": "button",
    "data-toggle": "dropdown",
    "aria-haspopup": "true",
    "aria-expanded": "false"
  }).text("Band 1").append($("<span>", { class: "caret" }))

  var uriInput = $("<input>", {
    type: "text",
    class: "form-control",
    "aria-describedby": "uriHelp",
    "placeholder": "Enter Cog URI"
  }).val(uri)
  var changeBand = function(update) {
    return function() {
      band = update;
      currentBandLabel.text("Band " + update);
    }
  }
  var mkOption = function(bandNo) {
    return $("<li>")
      .append($("<a>", { class: "dropdown-item", href: "#" })
      .text(bandNo)
      .click(changeBand(bandNo)))
  }

  var uriForm = $("<div>", { class: "input-group cogform", id: id }).append(
    uriInput,
    currentBandLabel,
    $("<div>", { class: "input-group-btn" }).append(
      currentBandLabel,
      $("<ul>", { class: "dropdown-menu" }).append(
        _.map(_.range(1, 9), function(n) { return mkOption(n) })
      )
    )
  )
  return {
    dom: uriForm,
    getBand: function() { return parseInt(band); },
    getUri: function() { return uriInput.val(); }
  }
}

// produce an HTML element which represents a cog source
function CogForm(uri, band, weight, id) {
  var weight = weight || 1;
  var uri = uri || ""
  var band = band || 1
  var id = id || guid();

  var weightSlider = $('<input>', { type: "range", class: "custom-range", min: "0", max: "1", step: "0.1", value: weight })
  var uriBox = UriBox(id, uri, band)
  var DOM = uriBox.dom
    .append(weightSlider)
    .append(
      $("<button>", { class: "btn btn-danger" }).append(
        $("<span>", { class: "fas fa-minus remove-btn" })
      ).click(function(e) {
        removeCog(id);
        if (cogForms.length == MAX_COGS) { $("#addCog").attr("disabled", true) };
      })
    )

  return {
    id: id,
    dom: DOM,
    getCog: function() { return Cog(uriBox.getUri(), uriBox.getBand()) },
    getWeight: function() { return weightSlider.val(); }
  }
}

function generateTms() {
  return L.tileLayer(BASE_URL + APP_ID + "/{z}/{x}/{y}")
}

function initFromJson(json) {
  var parsed = JSON.parse(json)
  _.map(parsed, function(cog, id) {
    console.log(id, cog)
    var uri = cog.uri;
    var band = cog.band;
    var weight = cog.weight;
    addCog(CogForm(uri, band, weight, id));
  })
}

function initFresh() {
  var default1 = CogForm("https://my.cog.source/1");
  var default2 = CogForm("https://my.cog.source/2");
  addCog(default1)
  addCog(default2)
}


$(window).ready(function() {
  var storedAppId = localStorage.getItem("cog-app-id")
  if (storedAppId) {
    APP_ID = storedAppId;
  } else {
    APP_ID = guid();
    localStorage.setItem("cog-app-id", APP_ID);
  }

  $("#addCog").click(function() {
    if (cogForms.length < MAX_COGS + 1) { addCog(CogForm()); };
    if (cogForms.length == MAX_COGS) { $("#addCog").attr("disabled", true) };
  });

  $("#apply").click(function() {
    var json = JSON.stringify(paramMap(cogForms));
    localStorage.setItem("cog-json", json);
    $.ajax({
      type: "POST",
      url: BASE_URL + APP_ID,
      data: json
    });

    if (overlayLayer) {
      map.removeLayer(overlayLayer);
    }
    overlayLayer = generateTms();
    map.addLayer(overlayLayer);
  });

  var storedCogs = localStorage.getItem("cog-json")
  if (storedCogs) {
    initFromJson(storedCogs);
  } else {
    initFresh();
  }
});


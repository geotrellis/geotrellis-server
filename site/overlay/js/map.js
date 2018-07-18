// GLOBAL STATE/MANAGEMENT FUNCS
var cogForms = [];
function addCog(cog) { cogForms.push(cog) }
function removeCog(id) {
  cogForms = _.filter(cogForms, function(cf) { return cf.id !== id })
  $("#" + id).remove()
}

// render the HTML form (idempotent!)
function instantiateForms(cogForms) {
  _.each(cogForms, function(cf) {
    $("#sidebody").append(cf.dom)
  });
}

// debugging helper
function printForms(cogForms) {
  _.each(cogForms, function(cf) {
    var cog = cf.getCog();
    var weight = cf.getWeight();
    console.log("ID", cf.id, "URI", cog.uri, "BAND", cog.band, "WEIGHT", weight);
  });
}

// for serialization of args to MAML
function ParamMap(cogForms) {
  var grouped = _.map(cogForms, function(cf) { return [cf.id, [cf.getCog(), cf.getWeight()]] })
  return _.object(grouped)
}


/** Spawn Map */
var map = L.map('map', { center: [0, 0], zoom: 2, zoomControl: false });
L.tileLayer('http://stamen-tiles-{s}.a.ssl.fastly.net/toner-lite/{z}/{x}/{y}.{ext}', {
  attribution: 'Map tiles by <a href="http://stamen.com">Stamen Design</a>, <a href="http://creativecommons.org/licenses/by/3.0">CC BY 3.0</a> &mdash; Map data &copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  subdomains: 'abcd',
  minZoom: 0,
  maxZoom: 20,
  ext: 'png'
}).addTo(map);
L.control.zoom({ position: 'topright' }).addTo(map);

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
  var uriForm = $("<div>", { class: "input-group cogform", id: id }).append(
    uriInput,
    currentBandLabel,
    $("<div>", { class: "input-group-btn" }).append(
      currentBandLabel,
      $("<ul>", { class: "dropdown-menu" }).append(
        $("<li>").append($("<a>", { class: "dropdown-item", href: "#" }).text("1").click(changeBand("1"))),
        $("<li>").append($("<a>", { class: "dropdown-item", href: "#" }).text("2").click(changeBand("2"))),
        $("<li>").append($("<a>", { class: "dropdown-item", href: "#" }).text("3").click(changeBand("3"))),
        $("<li>").append($("<a>", { class: "dropdown-item", href: "#" }).text("4").click(changeBand("4"))),
        $("<li>").append($("<a>", { class: "dropdown-item", href: "#" }).text("5").click(changeBand("5"))),
        $("<li>").append($("<a>", { class: "dropdown-item", href: "#" }).text("6").click(changeBand("6"))),
        $("<li>").append($("<a>", { class: "dropdown-item", href: "#" }).text("7").click(changeBand("7"))),
        $("<li>").append($("<a>", { class: "dropdown-item", href: "#" }).text("8").click(changeBand("8")))
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
function CogForm(uri, label, band, weight) {
  var weight = weight || 1;
  var uri = uri || ""
  var band = band || 1
  var id = guid();

  var weightSlider = $('<input>', { type: "range", class: "custom-range", min: "0", max: "1", step: "0.1", value: weight })
  var uriBox = UriBox(id, uri, band)
  var DOM = uriBox.dom
    .append(weightSlider)
    .append(
      $("<button>", { class: "btn btn-danger" }).append(
        $("<span>", { class: "fas fa-minus remove-btn" })
      ).click(function(e) {
        console.log("hit");
        removeCog(id);
      })
    )

  return {
    id: id,
    dom: DOM,
    getCog: function() { return Cog(uriBox.getUri(), uriBox.getBand()) },
    getWeight: function() { return weightSlider.val(); }
  }
}

// End of the world
var default1 = CogForm("https://my.cog.source/1");
var default2 = CogForm("https://my.cog.source/2");
addCog(default1)
addCog(default2)

$(window).ready(function() {
  $("#addCog").click(function() {
    if (cogForms.length < 7) {
      addCog(CogForm());
      instantiateForms(cogForms);
    }
    if (cogForms.length == 6) {
      $("#addCog").attr("disabled", true)
    }
  });
  // Initialize with defaults
  instantiateForms(cogForms);
});


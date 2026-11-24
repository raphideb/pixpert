// ----------------------------------------------------------------------------
// PixPert - GraXpert integration for PixInsight
// PixPert-params.jsh: parameters, defaults and their persistence
//
// Copyright (c) 2026 raphideb
// Released under the MIT License. See LICENSE.
// ----------------------------------------------------------------------------

#ifndef __PIXPERT_PARAMS_JSH
#define __PIXPERT_PARAMS_JSH

#define SETTINGS_KEY "PixPert"

// Background extraction methods, in the order shown by the dialog.
// 'cli' is the value GraXpert's -interpolation argument expects.
var BGE_METHODS = [
   { name: "AI",          cli: "AI",          needsGrid: false },
   { name: "Sample-free", cli: "sample_free", needsGrid: false },
   { name: "RBF",         cli: "RBF",         needsGrid: true  },
   { name: "Kriging",     cli: "Kriging",     needsGrid: true  },
   { name: "Splines",     cli: "Splines",     needsGrid: true  }
];

var CORRECTION_TYPES  = [ "Subtraction", "Division" ];
var RBF_KERNELS       = [ "thin_plate", "quintic", "cubic", "linear" ];
var DOWNSAMPLE_VALUES = [ 1, 2, 4, 8 ];
var DECONV_TYPES      = [ { name: "Object", cmd: "deconv-obj" },
                            { name: "Stellar", cmd: "deconv-stellar" } ];

// The model version shown first in the version combo boxes.
var LATEST_VERSION = "latest";

// Parameter definitions: name, type and default value. The defaults are the
// ones GraXpert itself ships with.
var PARAM_DEFS = [
   // Background extraction
   [ "bgeEnabled",          "b", true ],
   [ "bgeMethod",           "i", 0 ],          // index into BGE_METHODS
   [ "bgeCorrection",       "i", 0 ],          // index into CORRECTION_TYPES
   [ "bgeSmoothing",        "r", 0.5 ],
   [ "bgeAIVersion",        "s", LATEST_VERSION ],
   [ "bgeCreateBackground", "b", false ],
   // Sample-free (GraXpert 4)
   [ "sfScale",             "r", 5.0 ],
   [ "sfSmoothness",        "r", 1.0 ],
   [ "sfProtect",           "b", true ],
   [ "sfProtectThreshold",  "r", 0.05 ],
   [ "sfProtectAmount",     "r", 0.5 ],
   [ "sfSimplified",        "b", true ],
   [ "sfDegree",            "i", 1 ],
   [ "sfDownsample",        "i", 4 ],
   // Grid-based interpolation (RBF, Kriging, Splines)
   [ "gridPointsPerRow",    "i", 15 ],
   [ "gridTolerance",       "r", 1.0 ],
   [ "gridSampleSize",      "i", 25 ],
   [ "rbfKernel",           "i", 0 ],          // index into RBF_KERNELS
   [ "splineOrder",         "i", 3 ],
   // Deconvolution (only when the installed GraXpert offers it)
   [ "deconvEnabled",       "b", false ],
   [ "deconvType",          "i", 0 ],          // index into DECONV_TYPES
   [ "deconvStrength",      "r", 0.5 ],
   [ "deconvPsfSize",       "r", 5.0 ],
   [ "deconvObjAIVersion",  "s", LATEST_VERSION ],
   [ "deconvStarAIVersion", "s", LATEST_VERSION ],
   // Denoising
   [ "denoiseEnabled",      "b", false ],
   [ "denoiseStrength",     "r", 0.5 ],
   [ "denoiseAIVersion",    "s", LATEST_VERSION ],
   // General
   [ "gpuAcceleration",     "b", true ],
   [ "aiBatchSize",         "i", 4 ],
   [ "replaceTarget",       "b", true ],
   [ "timeoutMinutes",      "i", 30 ]
];

function PixPertParameters()
{
   this.graxpertPath = "";
   this.targetView = null;

   this.reset = function()
   {
      for ( let d of PARAM_DEFS )
         this[d[0]] = d[2];
   };

   // --- Persistent user settings (last used values) -------------------------

   this.load = function()
   {
      for ( let d of PARAM_DEFS )
      {
         let v = Settings.read( SETTINGS_KEY + "/" + d[0], settingsType( d[1] ) );
         if ( Settings.lastReadOK && v !== null && v !== undefined )
            this[d[0]] = v;
      }
      let p = Settings.read( SETTINGS_KEY + "/graxpertPath", DataType_String );
      if ( Settings.lastReadOK && p )
         this.graxpertPath = p;
   };

   this.save = function()
   {
      for ( let d of PARAM_DEFS )
         Settings.write( SETTINGS_KEY + "/" + d[0], settingsType( d[1] ), this[d[0]] );
      this.savePath();
   };

   this.savePath = function()
   {
      Settings.write( SETTINGS_KEY + "/graxpertPath", DataType_String, this.graxpertPath );
   };

   this.removeSettings = function()
   {
      for ( let d of PARAM_DEFS )
         Settings.remove( SETTINGS_KEY + "/" + d[0] );
   };

   // --- Script instance parameters (process icons, ProcessContainer) --------
   // The GraXpert path is a machine setting and is not stored in instances.

   this.exportParameters = function()
   {
      Parameters.clear();
      for ( let d of PARAM_DEFS )
         Parameters.set( d[0], this[d[0]] );
   };

   this.importParameters = function()
   {
      for ( let d of PARAM_DEFS )
         if ( Parameters.has( d[0] ) )
            switch ( d[1] )
            {
            case "b": this[d[0]] = Parameters.getBoolean( d[0] ); break;
            case "i": this[d[0]] = Parameters.getInteger( d[0] ); break;
            case "r": this[d[0]] = Parameters.getReal( d[0] ); break;
            default:  this[d[0]] = Parameters.getString( d[0] ); break;
            }
   };

   this.reset();
}

function settingsType( t )
{
   switch ( t )
   {
   case "b": return DataType_Boolean;
   case "i": return DataType_Int32;
   case "r": return DataType_Double;
   default:  return DataType_String;
   }
}

#endif // __PIXPERT_PARAMS_JSH

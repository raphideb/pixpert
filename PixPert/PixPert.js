// ----------------------------------------------------------------------------
// PixPert - GraXpert integration for PixInsight
//
// Copyright (c) 2026 raphideb
// Released under the MIT License. See LICENSE.
// PixPert is not affiliated with or endorsed by Pleiades Astrophoto S.L.
// or the GraXpert project.
// ----------------------------------------------------------------------------
//
// Runs the GraXpert command line on PixInsight images: AI, sample-free and
// interpolated (RBF, Kriging, Splines) background extraction, AI denoising,
// and deconvolution when the installed GraXpert offers it.
//
// GraXpert is a separate program (https://www.graxpert.com/) that must be
// installed on this computer. PixPert finds it in its default installation
// folder or uses the path set in its dialog.
// ----------------------------------------------------------------------------

#engine v8

#feature-id    PixPert : Utilities > PixPert (GraXpert)

#feature-info  Runs GraXpert on the active image: AI, sample-free and interpolated \
               background extraction, AI denoising, and deconvolution where the installed \
               GraXpert offers it. GraXpert must be installed separately.

#define TITLE           "PixPert (GraXpert)"
#define PIXPERT_VERSION "0.0.1"

#include <pjsr/ColorSpace.jsh>
#include <pjsr/DataType.jsh>
#include <pjsr/FrameStyle.jsh>
#include <pjsr/ImageOp.jsh>
#include <pjsr/ProcessExitStatus.jsh>
#include <pjsr/SampleType.jsh>
#include <pjsr/StdButton.jsh>
#include <pjsr/StdIcon.jsh>
#include <pjsr/TextAlign.jsh>
#include <pjsr/UndoFlag.jsh>

#include "PixPert-params.jsh"
#include "PixPert-engine.jsh"
#include "PixPert-gui.jsh"

// Makes sure params.graxpertPath points to an existing file; looks in the
// default folders otherwise. Returns true if GraXpert was found.
function pixpertResolvePath( params )
{
   if ( params.graxpertPath && File.exists( params.graxpertPath ) )
      return true;
   let found = pixpertFindGraXpert();
   if ( found )
   {
      params.graxpertPath = found;
      params.savePath();
      return true;
   }
   return false;
}

// Execution without the dialog: a script instance dropped onto an image, run
// in the global context, or run from a ProcessContainer.
function pixpertRunNonInteractive( params, view )
{
   params.importParameters();
   if ( !pixpertResolvePath( params ) )
   {
      console.criticalln( "*** PixPert: GraXpert executable not found. Open PixPert from the Script menu " +
                          "and set its path." );
      return;
   }
   let info = pixpertProbeGraXpert( params.graxpertPath, false );
   if ( !info.ok )
   {
      console.criticalln( "*** PixPert: " + info.error );
      return;
   }
   pixpertExecute( params, view, info );
}

function main()
{
   let params = new PixPertParameters();
   params.load();

   if ( Parameters.isViewTarget )
   {
      pixpertRunNonInteractive( params, Parameters.targetView );
      return;
   }
   if ( Parameters.isGlobalTarget )
   {
      let w = ImageWindow.activeWindow;
      if ( w.isNull )
         console.criticalln( "*** PixPert: no active image." );
      else
         pixpertRunNonInteractive( params, w.currentView );
      return;
   }

   // Opened from a process icon: start with the icon's parameters.
   if ( Parameters.has( "bgeEnabled" ) )
      params.importParameters();

   pixpertResolvePath( params );

   let w = ImageWindow.activeWindow;
   params.targetView = w.isNull ? null : w.currentView;

   console.hide();
   let dialog = new PixPertDialog( params );
   // PixInsight locks its workspace while a script runs, so the dialog closes
   // itself after a successful Apply (see PixPertDialog.apply).
   dialog.execute();
   params.save();
}

main();

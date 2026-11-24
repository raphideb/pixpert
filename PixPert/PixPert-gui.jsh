// ----------------------------------------------------------------------------
// PixPert - GraXpert integration for PixInsight
// PixPert-gui.jsh: the dialog
//
// Copyright (c) 2026 raphideb
// Released under the MIT License. See LICENSE.
// ----------------------------------------------------------------------------

#ifndef __PIXPERT_GUI_JSH
#define __PIXPERT_GUI_JSH

var PixPertDialog = class extends Dialog
{
   constructor( params )
   {
      super();

      let dlg = this;
      this.info = { ok: false, version: "", commands: [], commandInfo: {} };
      this.running = false;
      this.abortRequested = false;

      let labelWidth = this.font.width( "Polynomial degree:" ) + this.logicalPixelsToPhysical( 8 );
      let editWidth = this.font.width( "000000000" );

      // --- Small factories ---------------------------------------------------------
      // Controls that need a label are placed in a row control (.rowControl)
      // together with their label, so that showRow() can hide the whole row.

      let label = function( text, tip )
      {
         let l = new Label( dlg );
         l.text = text;
         l.minWidth = labelWidth;
         l.textAlignment = TextAlign_Right | TextAlign_VertCenter;
         if ( tip )
            l.toolTip = tip;
         return l;
      };

      // A row control holding the given controls (numbers are spacings), left aligned.
      let rowControl = function( items )
      {
         let r = new Control( dlg );
         r.sizer = new HorizontalSizer;
         r.sizer.spacing = 4;
         for ( let i of items )
            if ( typeof i == "number" )
               r.sizer.addSpacing( i );
            else
               r.sizer.add( i );
         r.sizer.addStretch();
         return r;
      };

      let attachRow = function( control, text, tip )
      {
         control.rowControl = rowControl( [ label( text, tip ), control ] );
         return control;
      };

      let numeric = function( text, min, max, precision, key, tip )
      {
         let c = new NumericControl( dlg );
         c.label.text = text;
         c.label.minWidth = labelWidth;
         c.setRange( min, max );
         c.slider.setRange( 0, 1000 );
         c.slider.minWidth = 240;
         c.setPrecision( precision );
         c.edit.minWidth = editWidth;
         c.setValue( params[key] );
         c.toolTip = tip;
         c.onValueUpdated = function( value ) { params[key] = value; };
         c.refresh = function() { c.setValue( params[key] ); };
         return c;
      };

      let spin = function( text, min, max, key, tip )
      {
         let s = new SpinBox( dlg );
         s.setRange( min, max );
         s.value = params[key];
         s.minWidth = editWidth;
         s.toolTip = tip;
         s.onValueUpdated = function( value ) { params[key] = value; };
         s.refresh = function() { s.value = params[key]; };
         return attachRow( s, text, tip );
      };

      let combo = function( text, items, key, tip, onChange )
      {
         let c = new ComboBox( dlg );
         for ( let i of items )
            c.addItem( i );
         c.currentItem = params[key];
         c.toolTip = tip;
         c.onItemSelected = function( index )
         {
            params[key] = index;
            if ( onChange )
               onChange();
         };
         c.refresh = function() { c.currentItem = params[key]; };
         return attachRow( c, text, tip );
      };

      let check = function( text, key, tip, onChange )
      {
         let c = new CheckBox( dlg );
         c.text = text;
         c.checked = params[key];
         c.toolTip = tip;
         c.onCheck = function( checked )
         {
            params[key] = checked;
            if ( onChange )
               onChange();
         };
         c.refresh = function() { c.checked = params[key]; };
         return attachRow( c, "", tip );
      };

      // Model version combo box. Item 0 is "latest"; the others are the versions
      // GraXpert reports, marked when they still have to be downloaded.
      let versionCombo = function( key, tip )
      {
         let c = new ComboBox( dlg );
         c.minWidth = dlg.font.width( "latest (0.0.00)  (will be downloaded)" ) + dlg.logicalPixelsToPhysical( 32 );
         c.versions = [];
         c.toolTip = tip;
         c.fill = function( commandInfo )
         {
            c.clear();
            c.versions = [ LATEST_VERSION ];
            let all = commandInfo ? commandInfo.versions : [];
            let local = commandInfo ? commandInfo.localVersions : [];
            c.addItem( all.length > 0 ? "latest (" + all[0] + ")" : "latest" );
            for ( let v of all )
            {
               c.versions.push( v );
               c.addItem( v + ( local.indexOf( v ) >= 0 ? "" : "  (will be downloaded)" ) );
            }
            if ( c.versions.indexOf( params[key] ) < 0 )
            {
               c.versions.push( params[key] );
               c.addItem( params[key] + "  (not listed)" );
            }
            c.refresh();
         };
         c.refresh = function() { c.currentItem = Math.max( 0, c.versions.indexOf( params[key] ) ); };
         c.onItemSelected = function( index ) { params[key] = c.versions[index]; };
         return attachRow( c, "AI model:", tip );
      };

      let group = function( title, items, checkKey )
      {
         let g = new GroupBox( dlg );
         g.title = title;
         g.sizer = new VerticalSizer;
         g.sizer.margin = 6;
         g.sizer.spacing = 4;
         for ( let i of items )
            g.sizer.add( i.rowControl ? i.rowControl : i );
         if ( checkKey )
         {
            g.titleCheckBox = true;
            g.checked = params[checkKey];
            g.onCheck = function( checked ) { params[checkKey] = checked; };
            g.refresh = function() { g.checked = params[checkKey]; };
         }
         return g;
      };

      // Shows or hides a control together with its row label.
      let showRow = function( control, show )
      {
         ( control.rowControl ? control.rowControl : control ).visible = show;
      };

      // --- Target ----------------------------------------------------------------

      this.viewList = new ViewList( this );
      this.viewList.getAll();
      if ( params.targetView && !params.targetView.isNull )
         this.viewList.currentView = params.targetView;
      this.viewList.toolTip = "<p>The image or preview to process.</p>";
      this.viewList.onViewSelected = function( view ) { params.targetView = view; };
      this.targetGroup = group( "Target", [ this.viewList ] );

      // --- GraXpert executable -------------------------------------------------------

      this.pathEdit = new Edit( this );
      this.pathEdit.text = params.graxpertPath;
      this.pathEdit.minWidth = this.font.width( "C:/Users/someone/AppData/Local/Programs/GraXpert/GraXpert.exe" );
      this.pathEdit.toolTip = "<p>Path of the GraXpert executable. PixPert remembers it.</p>";
      this.pathEdit.onEditCompleted = function()
      {
         let p = this.text.trim();
         if ( p != params.graxpertPath )
            dlg.setPath( p );
      };

      this.browseButton = new ToolButton( this );
      this.browseButton.icon = this.scaledResource( ":/icons/select-file.png" );
      this.browseButton.setScaledFixedSize( 22, 22 );
      this.browseButton.toolTip = "<p>Select the GraXpert executable.</p>";
      this.browseButton.onClick = function()
      {
         let fd = new OpenFileDialog;
         fd.caption = "Select the GraXpert executable";
         if ( pixpertIsWindows() )
            fd.filters = [ [ "Programs", "*.exe" ], [ "All files", "*" ] ];
         if ( params.graxpertPath && File.exists( params.graxpertPath ) )
            fd.initialPath = params.graxpertPath;
         if ( fd.execute() )
         {
            let p = fd.fileName;
            // A macOS application bundle: use the executable inside it.
            if ( p.endsWith( ".app" ) )
               p += "/Contents/MacOS/GraXpert";
            dlg.setPath( p );
         }
      };

      this.detectButton = new PushButton( this );
      this.detectButton.text = "Detect";
      this.detectButton.toolTip = "<p>Look for GraXpert in its default installation folders.</p>";
      this.detectButton.onClick = function()
      {
         let p = pixpertFindGraXpert();
         if ( p )
            dlg.setPath( p );
         else
            ( new MessageBox( "<p>GraXpert was not found in its default folders:</p><p>" +
                              pixpertCandidatePaths().map( pixpertEscape ).join( "<br>" ) +
                              "</p><p>Select the executable with the folder button.</p>",
                              TITLE, StdIcon_Warning, StdButton_Ok ) ).execute();
      };

      this.refreshButton = new PushButton( this );
      this.refreshButton.text = "Refresh";
      this.refreshButton.toolTip = "<p>Ask GraXpert again for its version, functions and AI models, " +
                                   "for example after updating GraXpert.</p>";
      this.refreshButton.onClick = function() { dlg.probe( true ); };

      this.statusLabel = new Label( this );
      this.statusLabel.useRichText = true;
      this.statusLabel.wordWrapping = true;

      let pathRow = new HorizontalSizer;
      pathRow.spacing = 4;
      pathRow.add( this.pathEdit, 100 );
      pathRow.add( this.browseButton );
      pathRow.add( this.detectButton );
      pathRow.add( this.refreshButton );

      this.graxpertGroup = group( "GraXpert executable", [ pathRow, this.statusLabel ] );

      // --- Background extraction ---------------------------------------------------

      this.bgeMethod = combo( "Method:", BGE_METHODS.map( m => m.name ), "bgeMethod",
         "<p><b>AI</b>: GraXpert's neural network models the background without samples.</p>" +
         "<p><b>Sample-free</b>: multiscale model without samples or AI (GraXpert 4 and newer).</p>" +
         "<p><b>RBF</b>, <b>Kriging</b>, <b>Splines</b>: interpolation between background samples. " +
         "PixPert places the samples on a grid, like GraXpert's <i>Create Grid</i>.</p>" +
         "<p>GraXpert 4.0 returns an empty model for <b>Splines</b> on its command line; PixPert then " +
         "stops and leaves the image unchanged.</p>",
         function() { dlg.updateControls(); } );
      this.bgeCorrection = combo( "Correction:", CORRECTION_TYPES, "bgeCorrection",
         "<p><b>Subtraction</b> removes additive gradients such as light pollution. " +
         "<b>Division</b> corrects multiplicative effects such as vignetting.</p>" );
      this.bgeSmoothing = numeric( "Smoothing:", 0, 1, 3, "bgeSmoothing",
         "<p>Smoothing of the background model, 0 to 1.</p>" );
      this.bgeAIVersion = versionCombo( "bgeAIVersion",
         "<p>Version of the background extraction AI model. GraXpert downloads it on first use.</p>" );

      // Sample-free
      this.sfScale = numeric( "Scale:", 1, 10, 2, "sfScale",
         "<p>Model scale, 1 to 10. Higher values model only larger structures.</p>" );
      this.sfSmoothness = numeric( "Smoothness:", 0, 10, 2, "sfSmoothness",
         "<p>Smoothing of the sample-free model, 0 or more.</p>" );
      this.sfProtect = check( "Protect structures", "sfProtect",
         "<p>Keep faint nebulosity and galaxies out of the background model.</p>",
         function() { dlg.updateControls(); } );
      this.sfProtectThreshold = numeric( "Protect threshold:", 0, 1, 3, "sfProtectThreshold",
         "<p>Brightness above the background, 0 to 1, from which structures are protected.</p>" );
      this.sfProtectAmount = numeric( "Protect amount:", 0, 1, 2, "sfProtectAmount",
         "<p>How strongly protected structures are kept out of the model, 0 to 1.</p>" );
      this.sfSimplified = new CheckBox( this );
      this.sfSimplified.text = "Fit a polynomial first";
      this.sfSimplified.toolTip = "<p>Fit a polynomial of the given degree before the multiscale extraction.</p>";
      this.sfSimplified.onCheck = function( checked ) { params.sfSimplified = checked; dlg.updateControls(); };
      this.sfSimplified.refresh = function() { dlg.sfSimplified.checked = params.sfSimplified; };
      this.sfSimplified.refresh();

      this.sfDegree = new SpinBox( this );
      this.sfDegree.setRange( 1, 6 );
      this.sfDegree.toolTip = "<p>Degree of the polynomial, 1 to 6.</p>";
      this.sfDegree.onValueUpdated = function( value ) { params.sfDegree = value; };
      this.sfDegree.refresh = function() { dlg.sfDegree.value = params.sfDegree; };
      this.sfDegree.refresh();
      this.sfDegreeLabel = new Label( this );
      this.sfDegreeLabel.text = "Degree:";
      this.sfDegreeLabel.textAlignment = TextAlign_Right | TextAlign_VertCenter;

      this.sfDownsample = new ComboBox( this );
      for ( let v of DOWNSAMPLE_VALUES )
         this.sfDownsample.addItem( v.toString() );
      this.sfDownsample.toolTip = "<p>Downsampling factor used while modelling. Higher is faster.</p>";
      this.sfDownsample.refresh = function()
      {
         dlg.sfDownsample.currentItem = Math.max( 0, DOWNSAMPLE_VALUES.indexOf( params.sfDownsample ) );
      };
      this.sfDownsample.refresh();
      this.sfDownsample.onItemSelected = function( index ) { params.sfDownsample = DOWNSAMPLE_VALUES[index]; };
      attachRow( this.sfDownsample, "Downsample:", this.sfDownsample.toolTip );

      this.sfPolynomial = { rowControl: rowControl( [ label( "" ), this.sfSimplified, 12, this.sfDegreeLabel, this.sfDegree ] ),
                            refresh: function() {} };

      this.sampleFreeControls = [ this.sfScale, this.sfSmoothness, this.sfProtect, this.sfProtectThreshold,
                                  this.sfProtectAmount, this.sfPolynomial, this.sfDownsample ];

      // Grid-based interpolation
      this.gridPoints = spin( "Points per row:", 3, 100, "gridPointsPerRow",
         "<p>Number of sample points across the width of the image.</p>" );
      this.gridTolerance = numeric( "Grid tolerance:", -2, 6, 2, "gridTolerance",
         "<p>Samples brighter than the median plus this many MADs are dropped. " +
         "Lower values reject more samples; raise it if too few samples remain.</p>" );
      this.gridSampleSize = spin( "Sample size:", 5, 151, "gridSampleSize",
         "<p>Size in pixels of the square around each sample point.</p>" );
      this.rbfKernel = combo( "RBF kernel:", RBF_KERNELS, "rbfKernel",
         "<p>Kernel of the radial basis function interpolation.</p>" );
      this.splineOrder = spin( "Spline order:", 1, 5, "splineOrder", "<p>Order of the splines, 1 to 5.</p>" );

      this.bgeCreateBackground = check( "Show gradient (background model, stretched)", "bgeCreateBackground",
         "<p>Also open the background model GraXpert removed, as a new image. It is shown with an " +
         "automatic screen stretch, so the gradient becomes visible. The pixel data stays unstretched.</p>" );

      this.bgeGroup = group( "Background Extraction",
         [ this.bgeMethod, this.bgeCorrection, this.bgeSmoothing, this.bgeAIVersion ]
            .concat( this.sampleFreeControls )
            .concat( [ this.gridPoints, this.gridTolerance, this.gridSampleSize, this.rbfKernel, this.splineOrder,
                       this.bgeCreateBackground ] ),
         "bgeEnabled" );

      // --- Deconvolution ---------------------------------------------------------

      this.deconvNote = new Label( this );
      this.deconvNote.wordWrapping = true;
      this.deconvNote.useRichText = true;
      this.deconvType = combo( "Type:", DECONV_TYPES.map( t => t.name ), "deconvType",
         "<p><b>Object</b> sharpens nebulae and galaxies, <b>Stellar</b> tightens stars.</p>",
         function() { dlg.updateControls(); } );
      this.deconvStrength = numeric( "Strength:", 0, 1, 2, "deconvStrength", "<p>Deconvolution strength, 0 to 1.</p>" );
      this.deconvPsfSize = numeric( "PSF size:", 0, 20, 2, "deconvPsfSize", "<p>Size of the point spread function in pixels.</p>" );
      this.deconvObjAIVersion = versionCombo( "deconvObjAIVersion", "<p>Version of the object deconvolution model.</p>" );
      this.deconvStarAIVersion = versionCombo( "deconvStarAIVersion", "<p>Version of the stellar deconvolution model.</p>" );
      this.deconvGroup = group( "Deconvolution",
         [ this.deconvNote, this.deconvType, this.deconvStrength, this.deconvPsfSize,
           this.deconvObjAIVersion, this.deconvStarAIVersion ],
         "deconvEnabled" );

      // --- Denoising -------------------------------------------------------------

      this.denoiseStrength = numeric( "Strength:", 0, 1, 2, "denoiseStrength",
         "<p>Strength of the denoising, 0 to 1. GraXpert's default is 0.5.</p>" );
      this.denoiseAIVersion = versionCombo( "denoiseAIVersion",
         "<p>Version of the denoising AI model. GraXpert downloads it on first use.</p>" );
      this.denoiseGroup = group( "Denoising", [ this.denoiseStrength, this.denoiseAIVersion ], "denoiseEnabled" );

      // --- Options ---------------------------------------------------------------

      this.output = new ComboBox( this );
      this.output.addItem( "Replace target image" );
      this.output.addItem( "Create new image" );
      this.output.toolTip = "<p>Replace the target (undoable) or open the result as a new image.</p>";
      this.output.refresh = function() { dlg.output.currentItem = params.replaceTarget ? 0 : 1; };
      this.output.refresh();
      this.output.onItemSelected = function( index ) { params.replaceTarget = index == 0; };

      this.gpu = new CheckBox( this );
      this.gpu.text = "GPU acceleration";
      this.gpu.toolTip = "<p>Run the AI models on the graphics card. Turn it off if GraXpert fails on your GPU.</p>";
      this.gpu.onCheck = function( checked ) { params.gpuAcceleration = checked; };
      this.gpu.refresh = function() { dlg.gpu.checked = params.gpuAcceleration; };
      this.gpu.refresh();

      let spinBox = function( min, max, key, tip )
      {
         let b = new SpinBox( dlg );
         b.setRange( min, max );
         b.minWidth = editWidth;
         b.toolTip = tip;
         b.onValueUpdated = function( value ) { params[key] = value; };
         b.refresh = function() { b.value = params[key]; };
         b.refresh();
         return b;
      };
      this.batchSize = spinBox( 1, 32, "aiBatchSize",
         "<p>Number of image tiles the AI processes at once (denoising and deconvolution). " +
         "Large values can run out of memory.</p>" );
      this.timeout = spinBox( 0, 600, "timeoutMinutes",
         "<p>Stop GraXpert when one step takes longer than this. 0 waits indefinitely.</p>" );
      let timeoutLabel = new Label( this );
      timeoutLabel.text = "Timeout (min):";
      timeoutLabel.textAlignment = TextAlign_Right | TextAlign_VertCenter;
      timeoutLabel.toolTip = this.timeout.toolTip;

      this.optionsGroup = group( "Options",
         [ rowControl( [ label( "Output:", this.output.toolTip ), this.output, 12, this.gpu ] ),
           rowControl( [ label( "AI batch size:", this.batchSize.toolTip ), this.batchSize, 12, timeoutLabel, this.timeout ] ) ] );

      // --- Buttons ---------------------------------------------------------------

      this.newInstanceButton = new ToolButton( this );
      this.newInstanceButton.icon = this.scaledResource( ":/process-interface/new-instance.png" );
      this.newInstanceButton.setScaledFixedSize( 24, 24 );
      this.newInstanceButton.toolTip = "<p>New instance: drag onto an image to apply these settings, " +
                                       "onto the workspace to keep them as a process icon, or into a ProcessContainer.</p>";
      this.newInstanceButton.onMousePress = function()
      {
         this.hasFocus = true;
         this.pushed = false;
         params.exportParameters();
         dlg.newInstance();
      };

      this.resetButton = new ToolButton( this );
      this.resetButton.icon = this.scaledResource( ":/process-interface/reset.png" );
      this.resetButton.setScaledFixedSize( 24, 24 );
      this.resetButton.toolTip = "<p>Reset all parameters to GraXpert's defaults. The GraXpert path is kept.</p>";
      this.resetButton.onClick = function()
      {
         params.reset();
         params.removeSettings();
         dlg.refreshControls();
      };

      this.applyButton = new PushButton( this );
      this.applyButton.text = "Apply";
      this.applyButton.icon = this.scaledResource( ":/icons/power.png" );
      this.applyButton.toolTip = "<p>Run GraXpert on the target and close PixPert, so you can inspect " +
                                 "the result. Your settings are kept for the next time. After an error " +
                                 "PixPert stays open. While GraXpert runs, this button stops it.</p>";
      this.applyButton.onClick = function() { dlg.apply(); };

      this.closeButton = new PushButton( this );
      this.closeButton.text = "Close";
      this.closeButton.icon = this.scaledResource( ":/icons/close.png" );
      this.closeButton.onClick = function()
      {
         if ( dlg.running )
            dlg.abortRequested = true;
         else
            dlg.ok();
      };

      let buttons = new HorizontalSizer;
      buttons.spacing = 6;
      buttons.add( this.newInstanceButton );
      buttons.add( this.resetButton );
      buttons.addStretch();
      buttons.add( this.applyButton );
      buttons.add( this.closeButton );

      // --- Layout ----------------------------------------------------------------

      this.sizer = new VerticalSizer;
      this.sizer.margin = 8;
      this.sizer.spacing = 6;
      this.sizer.add( this.targetGroup );
      this.sizer.add( this.graxpertGroup );
      this.sizer.add( this.bgeGroup );
      this.sizer.add( this.deconvGroup );
      this.sizer.add( this.denoiseGroup );
      this.sizer.add( this.optionsGroup );
      this.sizer.addSpacing( 4 );
      this.sizer.add( buttons );

      this.windowTitle = TITLE + " " + PIXPERT_VERSION;

      // --- Behaviour -------------------------------------------------------------

      this.setPath = function( p )
      {
         params.graxpertPath = p;
         params.savePath();
         this.pathEdit.text = p;
         this.probe( false );
      };

      this.probe = function( force )
      {
         if ( !params.graxpertPath )
            this.info = { ok: false, version: "", commands: [], commandInfo: {}, error: "No GraXpert executable set." };
         else
         {
            this.statusLabel.text = "Querying GraXpert...";
            processEvents();
            this.info = pixpertProbeGraXpert( params.graxpertPath, force );
         }
         this.bgeAIVersion.fill( this.info.commandInfo["background-extraction"] );
         this.denoiseAIVersion.fill( this.info.commandInfo["denoising"] );
         this.deconvObjAIVersion.fill( this.info.commandInfo["deconv-obj"] );
         this.deconvStarAIVersion.fill( this.info.commandInfo["deconv-stellar"] );
         this.updateControls();
      };

      this.deconvAvailable = function()
      {
         return this.info.ok && ( this.info.commands.indexOf( "deconv-obj" ) >= 0 ||
                                  this.info.commands.indexOf( "deconv-stellar" ) >= 0 );
      };

      this.updateControls = function()
      {
         let info = this.info;
         if ( info.ok )
            this.statusLabel.text = "GraXpert <b>" + pixpertEscape( info.version ) + "</b> found. Functions: " +
                                    info.commands.join( ", " ) + ".";
         else
            this.statusLabel.text = "<b>" + pixpertEscape( info.error || "GraXpert not found." ) + "</b> " +
                                    "Click <i>Detect</i> or select the executable with the folder button.";

         let method = BGE_METHODS[params.bgeMethod];
         showRow( this.bgeAIVersion, method.cli == "AI" );
         for ( let c of this.sampleFreeControls )
            showRow( c, method.cli == "sample_free" );
         for ( let c of [ this.gridPoints, this.gridTolerance, this.gridSampleSize ] )
            showRow( c, method.needsGrid );
         showRow( this.rbfKernel, method.cli == "RBF" );
         showRow( this.splineOrder, method.cli == "Splines" );
         this.sfProtectThreshold.enabled = params.sfProtect;
         this.sfProtectAmount.enabled = params.sfProtect;
         this.sfDegree.enabled = params.sfSimplified;
      this.sfDegreeLabel.enabled = params.sfSimplified;

         let deconv = this.deconvAvailable();
         this.deconvNote.visible = !deconv;
         this.deconvNote.text = "<i>Not available: " + ( info.ok ? "GraXpert " + pixpertEscape( info.version ) : "GraXpert" ) +
                                " offers no deconvolution on its command line (GraXpert 3.1 did).</i>";
         for ( let c of [ this.deconvType, this.deconvStrength, this.deconvPsfSize ] )
            showRow( c, deconv );
         showRow( this.deconvObjAIVersion, deconv && params.deconvType == 0 );
         showRow( this.deconvStarAIVersion, deconv && params.deconvType == 1 );
         if ( !deconv )
         {
            params.deconvEnabled = false;
            this.deconvGroup.checked = false;
         }
         this.deconvGroup.enabled = deconv && !this.running;
         this.applyButton.enabled = info.ok;

         this.setVariableSize();
         this.adjustToContents();
         this.setFixedSize();
      };

      this.refreshControls = function()
      {
         for ( let c of [ this.bgeMethod, this.bgeCorrection, this.bgeSmoothing, this.bgeAIVersion,
                          this.sfScale, this.sfSmoothness, this.sfProtect, this.sfProtectThreshold,
                          this.sfProtectAmount, this.sfSimplified, this.sfDegree, this.sfDownsample,
                          this.gridPoints, this.gridTolerance, this.gridSampleSize, this.rbfKernel,
                          this.splineOrder, this.bgeCreateBackground, this.bgeGroup,
                          this.deconvType, this.deconvStrength, this.deconvPsfSize,
                          this.deconvObjAIVersion, this.deconvStarAIVersion, this.deconvGroup,
                          this.denoiseStrength, this.denoiseAIVersion, this.denoiseGroup,
                          this.output, this.gpu, this.batchSize, this.timeout ] )
            c.refresh();
         this.updateControls();
      };

      this.setRunning = function( running )
      {
         this.running = running;
         for ( let c of [ this.targetGroup, this.graxpertGroup, this.bgeGroup, this.denoiseGroup,
                          this.optionsGroup, this.newInstanceButton, this.resetButton ] )
            c.enabled = !running;
         this.deconvGroup.enabled = !running && this.deconvAvailable();
         this.applyButton.text = running ? "Stop" : "Apply";
         processEvents();
      };

      this.apply = function()
      {
         if ( this.running )
         {
            this.abortRequested = true;
            return;
         }
         let view = this.viewList.currentView;
         if ( !view || view.isNull )
         {
            ( new MessageBox( "<p>Select a target image first.</p>", TITLE, StdIcon_Warning, StdButton_Ok ) ).execute();
            return;
         }
         if ( !this.info.ok )
         {
            ( new MessageBox( "<p>GraXpert was not found. Set the path to the GraXpert executable first.</p>",
                              TITLE, StdIcon_Warning, StdButton_Ok ) ).execute();
            return;
         }
         params.save();
         this.abortRequested = false;
         this.setRunning( true );
         let success = false;
         try
         {
            success = pixpertExecute( params, view, this.info, { isAborted: function() { return dlg.abortRequested; } } );
         }
         finally
         {
            this.setRunning( false );
         }
         // PixInsight's workspace is locked while a script runs. Closing after
         // a successful run ends the script, so the result can be inspected.
         // After an error or Stop the dialog stays open for another try.
         if ( success )
            this.ok();
      };

      // Closing the window while GraXpert runs stops GraXpert.
      this.onClose = function()
      {
         if ( this.running )
            this.abortRequested = true;
      };

      this.probe( false );
   }
};

#endif // __PIXPERT_GUI_JSH

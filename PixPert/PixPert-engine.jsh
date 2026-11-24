// ----------------------------------------------------------------------------
// PixPert - GraXpert integration for PixInsight
// PixPert-engine.jsh: locating and probing GraXpert, running it, image I/O,
// automatic sample grid and the processing pipeline
//
// Copyright (c) 2026 raphideb
// Released under the MIT License. See LICENSE.
// ----------------------------------------------------------------------------

#ifndef __PIXPERT_ENGINE_JSH
#define __PIXPERT_ENGINE_JSH

// Timeout for the quick calls that only ask GraXpert for its version or help.
#define PROBE_TIMEOUT_MS 120000

// ----------------------------------------------------------------------------
// Locating GraXpert
// ----------------------------------------------------------------------------

function pixpertIsWindows()
{
   return System.platform == "Windows" || System.platform == "MSWINDOWS";
}

function pixpertIsMac()
{
   return System.platform == "macOS" || System.platform == "MACOSX";
}

function pixpertEnv( name )
{
   try
   {
      let v = System.getEnvironmentVariable( name );
      return v ? File.windowsPathToUnix( v ) : "";
   }
   catch ( x )
   {
      return "";
   }
}

// Every place a standard GraXpert installation ends up in, most likely first.
function pixpertCandidatePaths()
{
   let list = [];
   if ( pixpertIsWindows() )
   {
      let localAppData = pixpertEnv( "LOCALAPPDATA" );
      let userProfile = pixpertEnv( "USERPROFILE" );
      let programFiles = pixpertEnv( "ProgramFiles" ) || "C:/Program Files";
      let programFilesX86 = pixpertEnv( "ProgramFiles(x86)" ) || "C:/Program Files (x86)";
      if ( localAppData )
         list.push( localAppData + "/Programs/GraXpert/GraXpert.exe" );
      if ( userProfile )
         list.push( userProfile + "/AppData/Local/Programs/GraXpert/GraXpert.exe" );
      list.push( programFiles + "/GraXpert/GraXpert.exe" );
      list.push( programFilesX86 + "/GraXpert/GraXpert.exe" );
      list.push( "C:/GraXpert/GraXpert.exe" );
   }
   else if ( pixpertIsMac() )
   {
      list.push( "/Applications/GraXpert.app/Contents/MacOS/GraXpert" );
      list.push( File.homeDirectory + "/Applications/GraXpert.app/Contents/MacOS/GraXpert" );
   }
   else
   {
      let home = File.homeDirectory;
      list.push( "/usr/bin/graxpert", "/usr/local/bin/graxpert", "/opt/GraXpert/GraXpert",
                 "/opt/graxpert/GraXpert", home + "/.local/bin/graxpert", home + "/GraXpert/GraXpert" );
      for ( let dir of [ home, home + "/Applications", home + "/Downloads", "/opt" ] )
         try
         {
            let found = File.searchDirectory( dir + "/GraXpert*.AppImage", false );
            if ( found )
               for ( let f of found )
                  list.push( f );
         }
         catch ( x )
         {
         }
   }
   return list.filter( ( p, i ) => list.indexOf( p ) == i );
}

// Returns the first existing candidate path, or an empty string.
function pixpertFindGraXpert()
{
   for ( let p of pixpertCandidatePaths() )
      if ( File.exists( p ) )
         return p;
   return "";
}

// ----------------------------------------------------------------------------
// Running GraXpert
// ----------------------------------------------------------------------------

// GraXpert prefixes every log line with a timestamp, the process name, the
// logger and the level. Returns { level, text } without that prefix.
function pixpertParseLogLine( line )
{
   let m = /^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d,\d+ \S+ \S+ ([A-Z]+)\s?(.*)$/.exec( line );
   if ( m )
      return { level: m[1], text: m[2] };
   return { level: "", text: line };
}

function pixpertEscape( text )
{
   return text.replace( /&/g, "&amp;" ).replace( /</g, "&lt;" ).replace( />/g, "&gt;" );
}

function pixpertEchoLine( line )
{
   let l = pixpertParseLogLine( line.replace( /\r$/, "" ) );
   if ( l.text.trim().length == 0 )
      return;
   let text = "<raw>" + l.text + "</raw>";
   if ( l.level == "ERROR" || l.level == "CRITICAL" || /Traceback|Error:/.test( l.text ) )
      console.criticalln( text );
   else if ( l.level == "WARNING" )
      console.warningln( text );
   else
      console.writeln( text );
}

// Runs a program and waits for it while keeping PixInsight responsive.
// options:
//    echo        print the program's output to the console (default false)
//    timeoutMs   kill the program after this time (0 = never)
//    isAborted   function returning true when the user asked to stop
// Returns { exitCode, output, aborted, timedOut, failedToStart }.
function pixpertRun( program, args, options )
{
   options = options || {};
   let result = { exitCode: -1, output: "", aborted: false, timedOut: false, failedToStart: false };
   let pending = "";

   let collect = function( text )
   {
      if ( !text || text.length == 0 )
         return;
      result.output += text;
      if ( options.echo )
      {
         pending += text;
         let lines = pending.split( "\n" );
         pending = lines.pop();
         for ( let l of lines )
            pixpertEchoLine( l );
         console.flush();
      }
   };

   let readOutput = function( P )
   {
      collect( P.stdout.utf8ToString() );
      collect( P.stderr.utf8ToString() );
   };

   let P = new ExternalProcess;
   try
   {
      P.start( program, args );
   }
   catch ( x )
   {
      result.failedToStart = true;
      result.output = x.toString();
      return result;
   }

   if ( !P.waitForStarted( 30000 ) )
   {
      result.failedToStart = true;
      try { P.kill(); } catch ( x ) {}
      return result;
   }

   let start = Date.now();
   while ( !P.waitForFinished( 100 ) )
   {
      readOutput( P );
      CoreApplication.processEvents();

      if ( ( options.isAborted && options.isAborted() ) || console.abortRequested )
      {
         result.aborted = true;
         P.kill();
         P.waitForFinished( 5000 );
         break;
      }
      if ( options.timeoutMs > 0 && Date.now() - start > options.timeoutMs )
      {
         result.timedOut = true;
         P.kill();
         P.waitForFinished( 5000 );
         break;
      }
      if ( !P.isRunning && !P.isStarting )
         break;
   }

   readOutput( P );
   if ( pending.length > 0 && options.echo )
      pixpertEchoLine( pending );

   result.exitCode = ( P.exitStatus == ProcessExitStatus_Normal ) ? P.exitCode : -1;
   return result;
}

// ----------------------------------------------------------------------------
// Probing GraXpert: version, commands, options and AI model versions
// ----------------------------------------------------------------------------

// Removes the log prefixes and joins GraXpert's wrapped help text into one line.
function pixpertFlattenHelp( output )
{
   let lines = output.split( "\n" ).map( l => pixpertParseLogLine( l.replace( /\r$/, "" ) ).text );
   return lines.join( " " ).replace( /\s+/g, " " );
}

function pixpertParseVersionList( text, label )
{
   let m = new RegExp( label + ":\\s*\\[([^\\]]*)\\]" ).exec( text );
   if ( !m )
      return [];
   return m[1].split( "," ).map( s => s.trim().replace( /^['"]|['"]$/g, "" ) ).filter( s => s.length > 0 );
}

// Compares version strings like "3.0.2" numerically.
function pixpertCompareVersions( a, b )
{
   let pa = a.split( "." ), pb = b.split( "." );
   for ( let i = 0; i < Math.max( pa.length, pb.length ); ++i )
   {
      let x = parseInt( pa[i] || "0" ), y = parseInt( pb[i] || "0" );
      if ( isNaN( x ) || isNaN( y ) )
         return ( pa[i] || "" ).localeCompare( pb[i] || "" );
      if ( x != y )
         return x - y;
   }
   return 0;
}

function pixpertParseCommandHelp( output )
{
   let text = pixpertFlattenHelp( output );
   let options = [];
   let re = /\[(-[A-Za-z_]+)/g, m;
   while ( ( m = re.exec( text ) ) !== null )
      if ( options.indexOf( m[1] ) < 0 )
         options.push( m[1] );

   let local = pixpertParseVersionList( text, "available locally" );
   let remote = pixpertParseVersionList( text, "available remotely" );
   let all = local.slice();
   for ( let v of remote )
      if ( all.indexOf( v ) < 0 )
         all.push( v );
   all.sort( ( a, b ) => pixpertCompareVersions( b, a ) );

   return { options: options, localVersions: local, versions: all };
}

function pixpertCommandHasOption( info, command, option )
{
   let c = info.commandInfo[command];
   return c !== undefined && c.options.indexOf( option ) >= 0;
}

// Queries the GraXpert executable. Results are cached per executable (path,
// size and modification time), because every call starts GraXpert anew.
function pixpertProbeGraXpert( path, forceRefresh )
{
   let info = { ok: false, path: path, version: "", commands: [], commandInfo: {}, error: "" };

   if ( !path || !File.exists( path ) )
   {
      info.error = "GraXpert executable not found.";
      return info;
   }

   let signature = path;
   try
   {
      let fi = new FileInfo( path );
      signature += "|" + fi.size + "|" + fi.lastModified.getTime();
   }
   catch ( x )
   {
   }

   if ( !forceRefresh )
   {
      let cached = Settings.read( SETTINGS_KEY + "/probeCache", DataType_String );
      if ( Settings.lastReadOK && cached )
         try
         {
            let c = JSON.parse( cached );
            if ( c.signature == signature && c.info && c.info.ok )
               return c.info;
         }
         catch ( x )
         {
         }
   }

   console.writeln( "<end><cbr>PixPert: querying GraXpert at <raw>" + path + "</raw>" );
   console.flush();

   let v = pixpertRun( path, [ "-v" ], { timeoutMs: PROBE_TIMEOUT_MS } );
   if ( v.failedToStart )
   {
      info.error = "GraXpert could not be started.";
      return info;
   }
   let vm = /GraXpert version:\s*([0-9][\w.\-]*)/.exec( v.output );
   info.version = vm ? vm[1] : "unknown";

   let h = pixpertRun( path, [ "-cli", "-h" ], { timeoutMs: PROBE_TIMEOUT_MS } );
   let hm = /-cmd \{([^}]+)\}/.exec( pixpertFlattenHelp( h.output ) );
   info.commands = hm ? hm[1].split( "," ).map( s => s.trim() ) : [ "background-extraction" ];

   for ( let cmd of info.commands )
   {
      let r = pixpertRun( path, [ "-cli", "-cmd", cmd, "-h" ], { timeoutMs: PROBE_TIMEOUT_MS } );
      info.commandInfo[cmd] = pixpertParseCommandHelp( r.output );
   }

   info.ok = true;
   Settings.write( SETTINGS_KEY + "/probeCache", DataType_String,
                   JSON.stringify( { signature: signature, info: info } ) );
   return info;
}

// ----------------------------------------------------------------------------
// Image I/O
// ----------------------------------------------------------------------------

// Writes an image as an uncompressed 32-bit floating point XISF file.
function pixpertWriteImage( image, filePath )
{
   let F = new FileFormat( ".xisf", false/*toRead*/, true/*toWrite*/ );
   if ( F.isNull )
      throw new Error( "The XISF format is not available." );
   let f = new FileFormatInstance( F );
   if ( f.isNull )
      throw new Error( "Unable to instantiate the XISF format." );
   if ( !f.create( filePath, "no-compress-data no-embedded-data no-resolution" ) )
      throw new Error( "Unable to create file: " + filePath );
   let d = new ImageDescription;
   d.bitsPerSample = 32;
   d.ieeefpSampleFormat = true;
   if ( !f.setOptions( d ) )
      throw new Error( "Unable to set output file options: " + filePath );

   // Write a 32-bit float copy, whatever the sample type of the source.
   let tmp = new Image( image.width, image.height, image.numberOfChannels,
                        image.isColor ? ColorSpace_RGB : ColorSpace_Gray, 32, SampleType_Real );
   tmp.apply( image, ImageOp_Mov );
   let ok = f.writeImage( tmp );
   f.close();
   tmp.free();
   if ( !ok )
      throw new Error( "Error writing file: " + filePath );
}

function pixpertReadImage( filePath )
{
   let F = new FileFormat( File.extractExtension( filePath ).toLowerCase(), true/*toRead*/, false/*toWrite*/ );
   if ( F.isNull )
      throw new Error( "No installed file format can read " + filePath );
   let f = new FileFormatInstance( F );
   if ( f.isNull )
      throw new Error( "Unable to instantiate file format: " + F.name );
   let d = f.open( filePath, "verbosity 0" );
   if ( !d || d.length < 1 )
      throw new Error( "Unable to open file: " + filePath );
   let image = new Image( 1, 1, 1, ColorSpace_Gray, 32, SampleType_Real );
   let ok = f.readImage( image );
   f.close();
   if ( !ok )
      throw new Error( "Unable to read file: " + filePath );
   return image;
}

// ----------------------------------------------------------------------------
// Automatic sample grid for RBF, Kriging and Splines
// ----------------------------------------------------------------------------

// Places a regular grid of sample points like GraXpert's "Create Grid":
// every point moves to the darkest of its four neighbouring quadrants, then
// points whose local median lies more than 'tolerance' MADs above the global
// median are dropped (they most likely sit on stars or nebulosity).
// Returns [[x, y, 1], ...] in image coordinates.
function pixpertCreateGrid( image, pointsPerRow, tolerance, sampleSize )
{
   let W = image.width, H = image.height, c1 = image.numberOfChannels - 1;
   let full = new Rect( 0, 0, W, H );
   let globalMedian = image.median( full, 0, c1 );
   let mad = image.MAD( globalMedian, full, 0, c1 );
   let half = Math.max( 1, Math.floor( sampleSize / 2 ) );

   let boxMedian = function( cx, cy )
   {
      let r = new Rect( Math.max( 0, cx - half ), Math.max( 0, cy - half ),
                        Math.min( W, cx + half + 1 ), Math.min( H, cy + half + 1 ) );
      return image.median( r, 0, c1 );
   };

   let dist = Math.max( sampleSize, Math.floor( W / Math.max( 2, pointsPerRow ) ) );
   // Centre the grid on the image.
   let xStart = Math.max( half, Math.floor( ( W - 1 - dist * Math.floor( ( W - 1 ) / dist ) ) / 2 ) );
   let yStart = Math.max( half, Math.floor( ( H - 1 - dist * Math.floor( ( H - 1 ) / dist ) ) / 2 ) );

   let points = [];
   for ( let y = yStart; y < H - half; y += dist )
      for ( let x = xStart; x < W - half; x += dist )
      {
         // Darkest quadrant
         let best = { x: x, y: y, m: Number.MAX_VALUE };
         for ( let dy of [ -half, half ] )
            for ( let dx of [ -half, half ] )
            {
               let qx = Math.min( W - 1 - half, Math.max( half, x + dx ) );
               let qy = Math.min( H - 1 - half, Math.max( half, y + dy ) );
               let m = boxMedian( qx, qy );
               if ( m < best.m )
                  best = { x: qx, y: qy, m: m };
            }
         points.push( best );
      }

   let limit = globalMedian + tolerance * mad;
   let kept = points.filter( p => p.m <= limit ).map( p => [ p.x, p.y, 1 ] );
   return { points: kept, total: points.length };
}

// ----------------------------------------------------------------------------
// Processing pipeline
// ----------------------------------------------------------------------------

function pixpertTempDir()
{
   let base = File.systemTempDirectory;
   for ( let i = 0; ; ++i )
   {
      let dir = base + "/PixPert_" + Date.now().toString() + ( i > 0 ? "_" + i : "" );
      if ( !File.directoryExists( dir ) )
      {
         File.createDirectory( dir, true );
         return dir;
      }
   }
}

function pixpertRemoveDir( dir )
{
   try
   {
      let files = File.searchDirectory( dir + "/*", false );
      for ( let f of files )
         try { File.remove( f ); } catch ( x ) {}
      File.removeDirectory( dir );
   }
   catch ( x )
   {
      console.warningln( "<end><cbr>PixPert: could not remove temporary folder <raw>" + dir + "</raw>" );
   }
}

// Picks the model version to pass to GraXpert: an explicit choice, or the
// newest version GraXpert knows of for "latest". Returns "" to leave it to
// GraXpert.
function pixpertResolveVersion( info, command, chosen )
{
   if ( chosen && chosen != LATEST_VERSION )
      return chosen;
   let c = info.commandInfo[command];
   if ( c && c.versions.length > 0 )
      return c.versions[0];
   return "";
}

function PixPertError( message )
{
   this.message = message;
   this.toString = function() { return this.message; };
}

// Runs one GraXpert command; returns the path of the output image.
function pixpertRunCommand( params, info, command, inputPath, outputBase, extraArgs, ctx )
{
   let args = [ "-cli", "-cmd", command, "-output", outputBase ];
   if ( pixpertCommandHasOption( info, command, "-gpu" ) )
      args.push( "-gpu", params.gpuAcceleration ? "true" : "false" );
   args = args.concat( extraArgs );
   args.push( inputPath );

   console.noteln( "<end><cbr><br>PixPert: GraXpert " + command );
   console.writeln( "<raw>" + params.graxpertPath + " " + args.join( " " ) + "</raw>" );
   console.flush();

   let t0 = Date.now();
   let r = pixpertRun( params.graxpertPath, args, {
      echo: true,
      timeoutMs: Math.max( 0, params.timeoutMinutes ) * 60000,
      isAborted: ctx.isAborted
   } );

   if ( r.failedToStart )
      throw new PixPertError( "GraXpert could not be started: " + params.graxpertPath );
   if ( r.aborted )
      throw new PixPertError( "Aborted by the user." );
   if ( r.timedOut )
      throw new PixPertError( "GraXpert did not finish within " + params.timeoutMinutes + " minutes and was stopped." );

   let outputPath = outputBase + ".xisf";
   if ( r.exitCode != 0 || !File.exists( outputPath ) )
      throw new PixPertError( "GraXpert " + command + " failed (exit code " + r.exitCode + "). " +
                              "See the console for GraXpert's messages." );

   console.writeln( format( "<end><cbr>GraXpert %s finished in %.1f s", command, ( Date.now() - t0 ) / 1000 ) );
   return outputPath;
}

function pixpertBackgroundArgs( params, info, image, tmpDir )
{
   const cmd = "background-extraction";
   let method = BGE_METHODS[params.bgeMethod];
   let args = [];
   let has = opt => pixpertCommandHasOption( info, cmd, opt );

   if ( method.cli != "AI" && !has( "-interpolation" ) )
      throw new PixPertError( "This GraXpert version (" + info.version + ") offers only AI background extraction " +
                              "on its command line. Update GraXpert to use " + method.name + "." );
   if ( method.cli == "sample_free" && !has( "-sample_free_scale" ) )
      throw new PixPertError( "Sample-free extraction needs GraXpert 4.0 or newer (installed: " + info.version + ")." );

   if ( has( "-interpolation" ) )
      args.push( "-interpolation", method.cli );
   args.push( "-correction", CORRECTION_TYPES[params.bgeCorrection] );
   args.push( "-smoothing", format( "%.3f", params.bgeSmoothing ) );

   if ( method.cli == "AI" )
   {
      let v = pixpertResolveVersion( info, cmd, params.bgeAIVersion );
      if ( v && has( "-ai_version" ) )
         args.push( "-ai_version", v );
   }
   else if ( method.cli == "sample_free" )
   {
      args.push( "-sample_free_scale", format( "%.3f", params.sfScale ),
                 "-sample_free_smoothness", format( "%.3f", params.sfSmoothness ),
                 "-sample_free_protect", params.sfProtect ? "true" : "false",
                 "-sample_free_protect_threshold", format( "%.4f", params.sfProtectThreshold ),
                 "-sample_free_protect_amount", format( "%.3f", params.sfProtectAmount ),
                 "-sample_free_simplified", params.sfSimplified ? "true" : "false",
                 "-sample_free_degree", params.sfDegree.toString(),
                 "-sample_free_downsample", params.sfDownsample.toString() );
   }
   else
   {
      // RBF, Kriging and Splines need sample points, which GraXpert only
      // accepts through a preferences file.
      console.writeln( "<end><cbr>PixPert: creating the sample grid..." );
      let grid = pixpertCreateGrid( image, params.gridPointsPerRow, params.gridTolerance, params.gridSampleSize );
      console.writeln( format( "PixPert: %d of %d grid points kept", grid.points.length, grid.total ) );
      if ( grid.points.length < 4 )
         throw new PixPertError( "Only " + grid.points.length + " sample points remain after rejection. " +
                                 "Increase the grid tolerance or the points per row." );
      let prefs = {
         width: image.width,
         height: image.height,
         background_points: grid.points,
         bg_pts_option: params.gridPointsPerRow,
         bg_tol_option: params.gridTolerance,
         interpol_type_option: method.cli,
         smoothing_option: params.bgeSmoothing,
         sample_size: params.gridSampleSize,
         RBF_kernel: RBF_KERNELS[params.rbfKernel],
         spline_order: params.splineOrder,
         corr_type: CORRECTION_TYPES[params.bgeCorrection],
         ai_gpu_acceleration: params.gpuAcceleration
      };
      let prefsPath = tmpDir + "/grid-preferences.json";
      File.writeTextFile( prefsPath, JSON.stringify( prefs ) );
      args.push( "-preferences_file", prefsPath );
   }

   if ( has( "-bg" ) )
      args.push( "-bg" );
   return args;
}

function pixpertDeconvArgs( params, info )
{
   let type = DECONV_TYPES[params.deconvType];
   let cmd = type.cmd;
   let has = opt => pixpertCommandHasOption( info, cmd, opt );
   let args = [];
   if ( has( "-strength" ) )
      args.push( "-strength", format( "%.3f", params.deconvStrength ) );
   if ( has( "-psfsize" ) )
      args.push( "-psfsize", format( "%.3f", params.deconvPsfSize ) );
   if ( has( "-batch_size" ) )
      args.push( "-batch_size", params.aiBatchSize.toString() );
   let v = pixpertResolveVersion( info, cmd, type.cmd == "deconv-obj" ? params.deconvObjAIVersion : params.deconvStarAIVersion );
   if ( v && has( "-ai_version" ) )
      args.push( "-ai_version", v );
   return args;
}

function pixpertDenoiseArgs( params, info )
{
   const cmd = "denoising";
   let has = opt => pixpertCommandHasOption( info, cmd, opt );
   let args = [ "-strength", format( "%.3f", params.denoiseStrength ) ];
   if ( has( "-batch_size" ) )
      args.push( "-batch_size", params.aiBatchSize.toString() );
   let v = pixpertResolveVersion( info, cmd, params.denoiseAIVersion );
   if ( v && has( "-ai_version" ) )
      args.push( "-ai_version", v );
   return args;
}

function pixpertNewWindow( image, id, sourceWindow )
{
   let w = new ImageWindow( image.width, image.height, image.numberOfChannels, 32, true/*float*/,
                            image.isColor, id );
   w.mainView.beginProcess( UndoFlag_NoSwapFile );
   w.mainView.image.assign( image );
   w.mainView.endProcess();
   if ( sourceWindow )
   {
      w.keywords = sourceWindow.keywords;
      try { w.copyAstrometricSolution( sourceWindow ); } catch ( x ) {}
   }
   w.show();
   w.zoomToOptimalFit();
   return w;
}

// Sets an automatic screen stretch (the AutoSTF algorithm) on a view, so that
// a faint, smooth image such as a background model becomes visible. Only the
// display changes, not the pixel data.
// Each channel is stretched on its own. A background model is so smooth that
// its spread is tiny next to the level differences between the channels (light
// pollution is usually much stronger in red), so a linked stretch would turn
// one channel white and the others black.
function pixpertAutoStretch( view )
{
   const SHADOWS_CLIP = -2.8;  // in normalized MADs below the median
   const TARGET_BKG = 0.25;    // where the median ends up on screen

   let image = view.image;
   let full = new Rect( 0, 0, image.width, image.height );
   let rows = [ // c0, c1, m, r0, r1
      [ 0, 1, 0.5, 0, 1 ],
      [ 0, 1, 0.5, 0, 1 ],
      [ 0, 1, 0.5, 0, 1 ],
      [ 0, 1, 0.5, 0, 1 ]
   ];
   let n = image.isColor ? 3 : 1;
   for ( let c = 0; c < n; ++c )
   {
      let med = image.median( full, c, c );
      let mad = image.MAD( med, full, c, c ) * 1.4826;
      // A flat channel has no spread; fall back to a small fraction of its level.
      if ( !( mad > 0 ) )
         mad = Math.max( med * 0.01, 1.0e-6 );
      let c0 = Math.range( med + SHADOWS_CLIP * mad, 0, 0.999 );
      // The display maps [c0, 1] to [0, 1]; m puts the median at TARGET_BKG.
      let x = Math.range( ( med - c0 ) / ( 1 - c0 ), 1.0e-9, 1 );
      rows[c] = [ c0, 1, Math.mtf( TARGET_BKG, x ), 0, 1 ];
   }

   let stf = new ScreenTransferFunction;
   stf.STF = rows;
   stf.executeOn( view, false/*swapFile*/ );
}

function pixpertValidId( id )
{
   let s = id.replace( /[^A-Za-z0-9_]/g, "_" );
   return /^[A-Za-z_]/.test( s ) ? s : "_" + s;
}

// Runs the enabled GraXpert steps on a view.
// ctx.isAborted: optional function polled while GraXpert runs.
// Returns true on success; reports errors on the console and returns false.
function pixpertExecute( params, view, info, ctx )
{
   ctx = ctx || {};
   if ( !view || view.isNull )
   {
      console.criticalln( "PixPert: no target image." );
      return false;
   }
   if ( !params.bgeEnabled && !params.denoiseEnabled && !( params.deconvEnabled && info.commands.indexOf( DECONV_TYPES[params.deconvType].cmd ) >= 0 ) )
   {
      console.warningln( "PixPert: no operation is enabled." );
      return false;
   }

   console.show();
   console.abortEnabled = true;
   let tmpDir = pixpertTempDir();
   let t0 = Date.now();
   let success = false;
   let result = null, background = null;

   try
   {
      let source = view.image;
      console.noteln( "<end><cbr><br>PixPert: processing <raw>" + view.fullId + "</raw> (" +
                      source.width + " x " + source.height + ", " + source.numberOfChannels + " channel(s))" );

      let current = tmpDir + "/input.xisf";
      pixpertWriteImage( source, current );

      let step = 0;

      if ( params.bgeEnabled )
      {
         let base = tmpDir + "/step" + ( ++step ) + "_bge";
         let args = pixpertBackgroundArgs( params, info, source, tmpDir );
         current = pixpertRunCommand( params, info, "background-extraction", current, base, args, ctx );
         // GraXpert always writes the model (-bg). An empty model means the
         // extraction silently failed (GraXpert 4.0 does this for Splines):
         // stop instead of handing back the unchanged image as a result.
         if ( File.exists( base + "_background.xisf" ) )
         {
            background = pixpertReadImage( base + "_background.xisf" );
            if ( background.maximum() == 0 && background.minimum() == 0 )
               throw new PixPertError( "GraXpert returned an empty background model, so nothing was removed. " +
                                       "This happens with " + BGE_METHODS[params.bgeMethod].name +
                                       " in some GraXpert versions; try another method." );
            if ( !params.bgeCreateBackground )
            {
               background.free();
               background = null;
            }
         }
         else if ( params.bgeCreateBackground )
            console.warningln( "PixPert: GraXpert did not write a background model." );
      }

      if ( params.deconvEnabled )
      {
         let cmd = DECONV_TYPES[params.deconvType].cmd;
         if ( info.commands.indexOf( cmd ) < 0 )
            console.warningln( "PixPert: deconvolution skipped; GraXpert " + info.version + " does not offer it on its command line." );
         else
            current = pixpertRunCommand( params, info, cmd, current, tmpDir + "/step" + ( ++step ) + "_deconv",
                                         pixpertDeconvArgs( params, info ), ctx );
      }

      if ( params.denoiseEnabled )
         current = pixpertRunCommand( params, info, "denoising", current, tmpDir + "/step" + ( ++step ) + "_denoise",
                                      pixpertDenoiseArgs( params, info ), ctx );

      result = pixpertReadImage( current );
      if ( result.width != source.width || result.height != source.height )
         throw new PixPertError( "GraXpert returned an image of a different size (" +
                                 result.width + " x " + result.height + ")." );
      result.truncate( 0, 1 );

      if ( params.replaceTarget )
      {
         view.beginProcess( UndoFlag_PixelData );
         if ( result.numberOfChannels == view.image.numberOfChannels )
            view.image.apply( result, ImageOp_Mov );
         else
            view.image.assign( result );
         view.endProcess();
      }
      else
      {
         pixpertNewWindow( result, pixpertValidId( view.id + "_GraXpert" ), view.window );
      }

      if ( background )
      {
         let bw = pixpertNewWindow( background, pixpertValidId( view.id + "_background" ), view.window );
         pixpertAutoStretch( bw.mainView );
      }

      console.noteln( format( "<end><cbr>PixPert: done in %.1f s", ( Date.now() - t0 ) / 1000 ) );
      success = true;
   }
   catch ( x )
   {
      console.criticalln( "<end><cbr>*** PixPert: " + pixpertEscape( x.toString() ) );
      if ( !( x instanceof PixPertError ) && x.stack )
         console.writeln( "<raw>" + x.stack + "</raw>" );
   }
   finally
   {
      if ( result ) result.free();
      if ( background ) background.free();
      pixpertRemoveDir( tmpDir );
      console.abortEnabled = false;
   }
   return success;
}

#endif // __PIXPERT_ENGINE_JSH

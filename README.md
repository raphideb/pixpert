# PixPert: GraXpert for PixInsight

PixPert is a PixInsight script that hands an image to GraXpert and puts the result back into
PixInsight. It lets you use GraXpert without leaving PixInsight: you keep working there, and
GraXpert does the work in the background.

PixPert talks to GraXpert through its command line. GraXpert itself is a separate program, so
it must be installed on the same computer.

**Note** this script will most likely only work on Windows 11.

## What it can do

PixPert offers everything GraXpert's command line offers:
• background extraction with the AI model
• sample-free background extraction (GraXpert 4 and newer)
• background extraction by interpolation between samples, with RBF, Kriging or Splines
• AI denoising
• AI deconvolution, for objects or stars, when the installed GraXpert offers it on its command
  line

GraXpert 4.0 has no deconvolution on its command line, although GraXpert 3.1 had. PixPert asks
GraXpert what it supports, so the Deconvolution section turns itself on when you use a
GraXpert version that has it.

You can switch on several functions at once. They then run one after the other, in the order
background extraction, deconvolution, denoising.

## Installing

PixPert does not need an update repository. You register its folder once, and PixInsight lists
it in the Script menu from then on.

1. Copy the `PixPert` folder of this project anywhere you like, for example to
   `C:\Astro\Scripts\PixPert`.
2. In PixInsight, open **Script > Feature Scripts...**.
3. Click **Add** and select that `PixPert` folder.
4. Click **Done**.

PixPert now appears as **Script > Utilities > PixPert (GraXpert)**.

If you only want to try it once, choose **Script > Run Script File...** and open `PixPert.js`
instead. Nothing gets registered that way.

### Updating PixPert

Replace the files in the folder you registered. PixInsight picks up the new version the next
time you start the script. If you add or rename script files, open **Feature Scripts...** and
click **Done** again.

## Setting the path to GraXpert

On its first start, PixPert looks for GraXpert in the folders the GraXpert installers use. On
Windows, the first place it looks is `%LOCALAPPDATA%\Programs\GraXpert\GraXpert.exe`. It
also knows the usual places on macOS and Linux.

The path it finds is shown under **GraXpert executable**. Below it, PixPert reports the
GraXpert version and the functions that version offers, for example "GraXpert 4.0.0 found.
Functions: background-extraction, denoising."

There are three ways to change the path:
• type it into the field and press Enter
• click the folder button and select the GraXpert program
• click **Detect** to search the default folders again

PixPert remembers the path. Click **Refresh** after you update GraXpert, so that PixPert asks it
again for its version, functions and AI models.

## Using PixPert

PixPert works on linear images, just as GraXpert does. Run it before you stretch the image.

1. Open the image, and select it, or one of its previews, under **Target**.
2. Switch on the sections you need: **Background Extraction**, **Deconvolution** or
   **Denoising**. Each one has a check box in its title.
3. Choose the settings.
4. Click **Apply**.

The console shows GraXpert's progress. While GraXpert runs, the **Apply** button reads
**Stop**, and clicking it ends GraXpert.

After a successful run PixPert closes itself. PixInsight keeps its workspace locked for as long
as a script is running, so this is what lets you zoom into the result, pan around and compare it
with the original right away. Your settings are remembered: if you do not like the result, undo it,
open PixPert again, adjust what you want and click **Apply** once more. If something goes wrong, or
you stop GraXpert, PixPert stays open so you can read the message in the console and try again.

To run the same settings on several images without opening PixPert each time, keep them as a
process icon (see *Process icons and ProcessContainer* below) and drop the icon onto each image.

### Background extraction

The **Method** decides how the background model is built.

There are three kinds of method:
• **AI** lets GraXpert's neural network model the background. You can choose the version of
  the AI model under **AI model**. Versions marked "(will be downloaded)" are fetched by
  GraXpert the first time you use them.
• **Sample-free** builds a multiscale model without samples and without AI. **Protect
  structures** keeps faint nebulae and galaxies out of the model. **Fit a polynomial first**
  removes a simple gradient of the chosen **Degree** before the multiscale step.
• **RBF**, **Kriging** and **Splines** interpolate between background samples.

GraXpert's command line cannot take samples you place by hand. For the interpolation methods,
PixPert therefore places them itself, the same way GraXpert's *Create Grid* button does. It
lays a grid of **Points per row** over the image, moves each point to the darkest nearby spot,
and drops points that are clearly brighter than the background, because these usually sit on
stars or nebulae. Lower the **Grid tolerance** to drop more points. Raise it if PixPert reports
that too few points remain.

**Correction** chooses how the model is removed:
• **Subtraction** removes additive gradients such as light pollution.
• **Division** corrects multiplicative effects such as vignetting.

Tick **Show gradient** to see what was removed. The background model opens as a new image with
"_background" added to the name, for example `M33_background`. A gradient is usually far too faint
to see on its own, so PixPert gives this image an automatic screen stretch, the same kind the
AutoSTF button applies. Only the display is stretched; the pixel values stay as GraXpert wrote
them. Each colour channel is stretched on its own, so the shape of the gradient is visible in all
of them; places where red, green and blue change differently show up as a colour tint.

### Denoising and deconvolution

**Strength** sets how strongly GraXpert denoises or deconvolves, from 0 to 1. For
deconvolution, **Type** chooses between **Object** for nebulae and galaxies and **Stellar** for
stars, and **PSF size** sets the size of the stars' point spread function in pixels.

### Options

The **Options** section holds the settings that apply to every function:
• **Output** replaces the target image, or opens the result as a new image with "_GraXpert"
  added to its name. Replacing can be undone.
• **GPU acceleration** runs the AI on the graphics card. Switch it off if GraXpert fails on your
  GPU.
• **AI batch size** is the number of image tiles the AI processes at once. Large values can run
  out of memory.
• **Timeout (min)** stops GraXpert if one function takes longer than this. 0 waits for as long
  as it takes.

The reset button in the bottom left corner sets everything back to GraXpert's defaults. It
keeps the path to GraXpert.

## Process icons and ProcessContainer

Drag the triangle in the bottom left corner of the dialog onto the workspace to keep the current
settings as a process icon. You can then:
• drag the icon onto an image to process it without opening the dialog
• double-click it to open PixPert with those settings
• put it into a ProcessContainer together with other processes

The icon does not store the path to GraXpert. It uses the path PixPert remembers on this
computer.

## When something goes wrong

**"GraXpert executable not found."** PixPert found no GraXpert in the default folders. Set the
path as described in "Setting the path to GraXpert".

**GraXpert fails or stops with an error.** GraXpert's own messages are in the PixInsight
console. Its log file is also worth a look: on Windows it is
`%LOCALAPPDATA%\GraXpert\GraXpert\Logs\graxpert.log`. If the error mentions the GPU, switch off
**GPU acceleration**.

**"GraXpert returned an empty background model."** GraXpert ran but removed nothing. GraXpert
4.0 does this with **Splines** on its command line. PixPert stops in that case and leaves your
image unchanged. Use **RBF** or **Kriging** instead.

**The first AI run takes long.** GraXpert downloads an AI model the first time you use it.
Later runs are faster.

## License and trademarks

PixPert is free software under the MIT License. You may use, change and pass it on, as long as
the copyright notice stays with it. It comes without any warranty. The full text is in
[LICENSE](LICENSE).

PixPert is an independent project. It is not affiliated with or endorsed by Pleiades
Astrophoto S.L., the makers of PixInsight, or by the GraXpert project. PixInsight is a
trademark of Pleiades Astrophoto S.L., and GraXpert is a trademark of its owners. Both names
appear here only to say which programs PixPert works with.

GraXpert is installed separately and comes with its own license, the GPL-3.0. PixPert contains
none of GraXpert's code or AI models. It only starts the GraXpert program you installed.

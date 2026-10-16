using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

internal static class IconBuilder
{
    private static readonly int[] Sizes = { 16, 20, 24, 32, 40, 48, 64, 90, 128, 256 };

    public static int Main(string[] args)
    {
        if (args.Length != 2)
        {
            Console.Error.WriteLine("Usage: IconBuilder <source.png> <output.ico>");
            return 2;
        }

        try
        {
            List<byte[]> images = new List<byte[]>();
            using (Image source = Image.FromFile(args[0]))
            {
                for (int index = 0; index < Sizes.Length; index++)
                {
                    images.Add(RenderPng(source, Sizes[index]));
                }
            }

            using (FileStream stream = new FileStream(args[1], FileMode.Create, FileAccess.Write, FileShare.None))
            using (BinaryWriter writer = new BinaryWriter(stream))
            {
                writer.Write((ushort)0);
                writer.Write((ushort)1);
                writer.Write((ushort)images.Count);

                int offset = 6 + images.Count * 16;
                for (int index = 0; index < images.Count; index++)
                {
                    int size = Sizes[index];
                    writer.Write((byte)(size == 256 ? 0 : size));
                    writer.Write((byte)(size == 256 ? 0 : size));
                    writer.Write((byte)0);
                    writer.Write((byte)0);
                    writer.Write((ushort)1);
                    writer.Write((ushort)32);
                    writer.Write((uint)images[index].Length);
                    writer.Write((uint)offset);
                    offset += images[index].Length;
                }

                for (int index = 0; index < images.Count; index++)
                {
                    writer.Write(images[index]);
                }
            }

            Console.WriteLine("Created icon with " + images.Count + " sizes.");
            return 0;
        }
        catch (Exception exception)
        {
            Console.Error.WriteLine(exception);
            return 1;
        }
    }

    private static byte[] RenderPng(Image source, int size)
    {
        using (Bitmap bitmap = new Bitmap(size, size, PixelFormat.Format32bppArgb))
        using (Graphics graphics = Graphics.FromImage(bitmap))
        using (ImageAttributes attributes = new ImageAttributes())
        {
            graphics.Clear(Color.Transparent);
            graphics.CompositingMode = CompositingMode.SourceCopy;
            graphics.CompositingQuality = CompositingQuality.HighQuality;
            graphics.SmoothingMode = SmoothingMode.None;
            graphics.PixelOffsetMode = PixelOffsetMode.Half;
            graphics.InterpolationMode = size < source.Width
                ? InterpolationMode.HighQualityBicubic
                : InterpolationMode.NearestNeighbor;
            attributes.SetWrapMode(WrapMode.TileFlipXY);
            graphics.DrawImage(
                source,
                new Rectangle(0, 0, size, size),
                0,
                0,
                source.Width,
                source.Height,
                GraphicsUnit.Pixel,
                attributes);

            using (MemoryStream memory = new MemoryStream())
            {
                bitmap.Save(memory, ImageFormat.Png);
                return memory.ToArray();
            }
        }
    }
}

/**
 * Copyright (c) 2012 Jun Mei
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.nevralia;

import static com.googlecode.javacv.cpp.opencv_core.CV_AA;
import static com.googlecode.javacv.cpp.opencv_core.CV_FILLED;
import static com.googlecode.javacv.cpp.opencv_core.cvDrawContours;
import static com.googlecode.javacv.cpp.opencv_core.cvSet;
import static com.googlecode.javacv.cpp.opencv_imgproc.CV_CHAIN_APPROX_NONE;
import static com.googlecode.javacv.cpp.opencv_imgproc.CV_RETR_LIST;
import static com.googlecode.javacv.cpp.opencv_imgproc.CV_RGB2GRAY;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvCanny;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvCvtColor;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvFindContours;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.RecursiveAction;

import javax.imageio.ImageIO;

import com.googlecode.javacpp.Loader;
import com.googlecode.javacv.cpp.opencv_core.CvContour;
import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvScalar;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.IplImage;

/**
 * @author Jun Mei
 * 
 */
@SuppressWarnings("serial")
public class ContourProcessor extends RecursiveAction {

    private final List<File> images_;
    private final int start_;
    private final int end_;
    private final double threshold1_;
    private final double threshold2_;

    /**
     * 
     * @param images
     * @param start
     * @param end
     */
    public ContourProcessor(List<File> images, int start, int end, double t1,
        double t2) {
        super();
        this.images_ = images;
        this.start_ = start;
        this.end_ = end;
        this.threshold1_ = t1;
        this.threshold2_ = t2;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.util.concurrent.RecursiveAction#compute()
     */
    @Override
    protected void compute() {
        final short threshold = 8;
        if (this.end_ - this.start_ > threshold) {
            int midPoint = (this.start_ + this.end_) >>> 1;
            ContourProcessor task1 = new ContourProcessor(this.images_,
                this.start_, midPoint, this.threshold1_, this.threshold2_);
            ContourProcessor task2 = new ContourProcessor(this.images_,
                midPoint, this.end_, this.threshold1_, this.threshold2_);
            super.invokeAll(task1, task2);
        } else {
            for (int i = this.start_; i < this.end_; ++i) {
                process(this.images_.get(i));
            }
        }
    }

    private void process(File file) {
        BufferedImage img = null;

        try {
            img = ImageIO.read(file);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if (img == null) {
            return;
        }

        IplImage src = IplImage.createFrom(img);
        IplImage srcGray = IplImage.create(src.cvSize(), src.depth(), 1);
        cvCvtColor(src, srcGray, CV_RGB2GRAY);

        final int aperture = 3;
        IplImage tmp = IplImage.create(src.cvSize(), src.depth(), 1);
        cvCanny(srcGray, tmp, this.threshold1_, this.threshold2_, aperture);
        IplImage mask = createMask(tmp);
        BufferedImage output = applyMask(img, mask.getBufferedImage());

        try {
            File outputFile = getOutputFile(file);
            ImageIO.write(output, "png", outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static BufferedImage applyMask(BufferedImage img, BufferedImage mask) {
        int width = img.getWidth();
        int height = img.getHeight();
        int[] srcPixels = img.getRGB(0, 0, width, height, null, 0, width);
        int[] maskPixels = mask.getRGB(0, 0, width, height, null, 0, width);

        for (int i = 0; i < srcPixels.length; ++i) {
            int color = srcPixels[i] & 0x00ffffff;
            int alpha = maskPixels[i] << 24;
            srcPixels[i] = color | alpha;
        }

        BufferedImage result = new BufferedImage(width, height,
            BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, srcPixels, 0, width);
        return result;
    }

    /**
     * 
     * @param src
     * @return
     */
    private IplImage createMask(IplImage src) {
        IplImage result = IplImage.create(src.cvSize(), src.depth(), 1);
        cvSet(result, CvScalar.BLACK);
        CvMemStorage storage = CvMemStorage.create();

        CvSeq contour = new CvSeq(null);

        cvFindContours(src, storage, contour, Loader.sizeof(CvContour.class),
            CV_RETR_LIST, CV_CHAIN_APPROX_NONE);

        final int depth = -1;
        final CvScalar white = CvScalar.WHITE;

        for (int thickness = 1; thickness < 3; ++thickness) {
            while (contour != null && contour.isNull() == false) {
                if (contour.elem_size() > 0) {
                    cvDrawContours(result, contour, white, white, depth,
                        thickness, CV_AA);
                }
                contour = contour.h_next();
            }

            contour = new CvSeq(null);
            cvFindContours(result, storage, contour,
                Loader.sizeof(CvContour.class), CV_RETR_LIST,
                CV_CHAIN_APPROX_NONE);
        }

        while (contour != null && contour.isNull() == false) {
            if (contour.elem_size() > 0) {
                cvDrawContours(result, contour, white, white, depth, CV_FILLED,
                    CV_AA);
            }
            contour = contour.h_next();
        }

        return result;
    }

    private static File getOutputFile(File inputFile) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss.SSS");
        String timestamp = dateFormat.format(new Date());
        String src = inputFile.getPath();
        String path = String.format("%1$s-%2$s.png", src, timestamp);
        return new File(path);
    }
}
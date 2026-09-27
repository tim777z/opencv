package org.opencv.test.video;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.test.OpenCVTestCase;
import org.opencv.video.Video;

/**
 * Binding tests for {@link Video}.
 *
 * <p>Scope: these assert the contract of the generated JNI layer - argument
 * marshalling, in/out reference arguments, return shapes - for the free
 * functions of the video module. Numerical accuracy of the algorithms
 * themselves is covered by the C++ suite in modules/video/test
 * (test_camshift.cpp, test_estimaterigid.cpp, test_optflowpyrlk.cpp), which has
 * access to internal state and reference data.
 *
 * <p>Stubs for the motion-template API (segmentMotion, updateMotionHistory,
 * calcMotionGradient, calcGlobalOrientation) were removed: those entry points
 * are no longer part of the public C++ headers in this line, so the tests could
 * never be implemented. See docs/TESTING.md.
 */
public class VideoTest extends OpenCVTestCase {

    /** Side of the synthetic back-projection image built by makeProbImage(). */
    private static final int PROB_IMAGE_SIZE = 100;
    /** Centre (in pixels, 0-based) of the bright square inside makeProbImage(). */
    private static final double BLOB_CENTRE = 49.5;
    /** Side of that square; its centre of mass is BLOB_CENTRE. */
    private static final int BLOB_SIZE = 20;
    /**
     * Tolerance for centre-of-mass assertions. meanShift and CamShift work on an
     * integer search rectangle and round the result, so they settle up to a
     * pixel either side of the exact centre of mass.
     */
    private static final double CENTRE_TOLERANCE = 2.0;

    private MatOfFloat err = null;
    private int h;
    private MatOfPoint2f nextPts = null;
    private MatOfPoint2f prevPts = null;

    private int shift1;
    private int shift2;

    private MatOfByte status = null;
    private Mat subLena1 = null;
    private Mat subLena2 = null;
    private int w;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        shift1 = 10;
        shift2 = 17;
        w = (int)(rgbLena.cols() / 2);
        h = (int)(rgbLena.rows() / 2);

        subLena1 = rgbLena.submat(shift1, h + shift1, shift1, w + shift1);
        subLena2 = rgbLena.submat(shift2, h + shift2, shift2, w + shift2);

        prevPts = new MatOfPoint2f(new Point(11d, 8d), new Point(5d, 5d), new Point(10d, 10d));

        nextPts = new MatOfPoint2f();
        status = new MatOfByte();
        err = new MatOfFloat();
    }

    /**
     * Builds a back-projection-like probability image: a single bright square
     * centred on BLOB_CENTRE, everything else zero. The centre of mass of the
     * image is therefore known exactly, which is what meanShift/CamShift must
     * converge to.
     */
    private Mat makeProbImage() {
        int from = (int)(BLOB_CENTRE - BLOB_SIZE / 2);
        int to = from + BLOB_SIZE;

        Mat probImage = new Mat(PROB_IMAGE_SIZE, PROB_IMAGE_SIZE, CvType.CV_32F, new Scalar(0));
        probImage.submat(from, to, from, to).setTo(new Scalar(255));
        return probImage;
    }

    public void testCalcOpticalFlowFarneback() {
        Mat flow = new Mat();

        // The same frame twice: Farneback's polynomial expansion collapses and
        // the flow must be exactly zero everywhere.
        Video.calcOpticalFlowFarneback(grayChess, grayChess, flow, 0.5, 3, 15, 3, 5, 1.1, 0);

        assertEquals(grayChess.cols(), flow.cols());
        assertEquals(grayChess.rows(), flow.rows());
        assertEquals(CvType.CV_32FC2, flow.type());
        assertEquals(0, Core.countNonZero(flow));
    }

    public void testCalcOpticalFlowPyrLKMatMatListOfPointListOfPointListOfByteListOfFloat() {
        Video.calcOpticalFlowPyrLK(subLena1, subLena2, prevPts, nextPts, status, err);
        assertEquals(3, Core.countNonZero(status));
    }

    public void testCalcOpticalFlowPyrLKMatMatListOfPointListOfPointListOfByteListOfFloatSize() {
        Size sz = new Size(3, 3);
        Video.calcOpticalFlowPyrLK(subLena1, subLena2, prevPts, nextPts, status, err, sz, 3);
        assertEquals(0, Core.countNonZero(status));
    }

    public void testCalcOpticalFlowPyrLKMatMatListOfPointListOfPointListOfByteListOfFloatSizeIntTermCriteriaDoubleIntDouble() {
        // Explicit criteria/flags/minEigThreshold: the same call as above with
        // every optional argument spelled out. A 3x3 window cannot track, so
        // only the marshalling is asserted here.
        Size sz = new Size(3, 3);
        TermCriteria criteria = new TermCriteria(TermCriteria.EPS, 0, 0.01);

        Video.calcOpticalFlowPyrLK(subLena1, subLena2, prevPts, nextPts, status, err, sz, 3, criteria, 0, 1e-4);

        assertEquals(prevPts.rows(), status.rows());
        assertEquals(1, status.cols());
        assertEquals(CvType.CV_8U, status.type());
        assertEquals(prevPts.rows(), err.rows());
        assertEquals(1, err.cols());
        assertEquals(CvType.CV_32F, err.type());
        assertEquals(prevPts.rows(), nextPts.rows());
        assertEquals(1, nextPts.cols());
        assertEquals(CvType.CV_32FC2, nextPts.type());
    }

    public void testCamShift() {
        Rect window = new Rect(33, 33, 40, 40);

        RotatedRect box = Video.CamShift(makeProbImage(), window, new TermCriteria(TermCriteria.EPS, 0, 0.5));

        // The window is an in/out argument: CamShift must have moved it onto the
        // blob and must leave a valid, image-bounded search rectangle behind.
        assertTrue(window.x >= 0);
        assertTrue(window.y >= 0);
        assertTrue(window.x + window.width <= PROB_IMAGE_SIZE);
        assertTrue(window.y + window.height <= PROB_IMAGE_SIZE);

        // The returned box is centred on the centre of mass of the blob.
        assertEquals(BLOB_CENTRE, box.center.x, CENTRE_TOLERANCE);
        assertEquals(BLOB_CENTRE, box.center.y, CENTRE_TOLERANCE);

        // A symmetric blob has no orientation and no aspect ratio, and CAMSHIFT
        // must recover its size to well within a factor of two.
        assertEquals((double)box.size.width, (double)box.size.height, weakEPS);
        assertTrue("recovered size " + box.size.width, box.size.width > BLOB_SIZE / 2);
        assertTrue("recovered size " + box.size.width, box.size.width < BLOB_SIZE * 3 / 2);
    }

    public void testEstimateRigidTransform() {
        MatOfPoint2f src = new MatOfPoint2f(new Point(0, 0), new Point(10, 0), new Point(0, 10), new Point(10, 10));
        MatOfPoint2f dst = new MatOfPoint2f(new Point(2, 3), new Point(12, 3), new Point(2, 13), new Point(12, 13));

        // fullAffine == false: rotation + uniform scale + translation must be
        // able to express a pure translation, and recover it exactly.
        Mat m = Video.estimateRigidTransform(src, dst, false);

        assertEquals(2, m.rows());
        assertEquals(3, m.cols());
        assertEquals(CvType.CV_64F, m.type());
        assertEquals(1.0, m.get(0, 0)[0], EPS);
        assertEquals(0.0, m.get(0, 1)[0], EPS);
        assertEquals(2.0, m.get(0, 2)[0], EPS);
        assertEquals(0.0, m.get(1, 0)[0], EPS);
        assertEquals(1.0, m.get(1, 1)[0], EPS);
        assertEquals(3.0, m.get(1, 2)[0], EPS);

        // With fullAffine == true the general 2x3 model is used; the same data
        // must still be reproduced exactly.
        Mat mFull = Video.estimateRigidTransform(src, dst, true);

        assertEquals(2, mFull.rows());
        assertEquals(3, mFull.cols());
        assertEquals(CvType.CV_64F, mFull.type());
        assertEquals(1.0, mFull.get(0, 0)[0], EPS);
        assertEquals(0.0, mFull.get(0, 1)[0], EPS);
        assertEquals(2.0, mFull.get(0, 2)[0], EPS);
        assertEquals(0.0, mFull.get(1, 0)[0], EPS);
        assertEquals(1.0, mFull.get(1, 1)[0], EPS);
        assertEquals(3.0, mFull.get(1, 2)[0], EPS);
    }

    public void testMeanShift() {
        // Initial window is 3 pixels right/below the centre of mass, so a
        // correct meanShift has to move it back.
        Rect window = new Rect(33, 33, 40, 40);

        int iterations = Video.meanShift(makeProbImage(), window, new TermCriteria(TermCriteria.EPS, 0, 0.5));

        assertTrue("meanShift did not run", iterations > 0);

        // meanShift only translates: the window keeps its size.
        assertEquals(40, window.width);
        assertEquals(40, window.height);

        // ... and it ends up centred on the blob (see CENTRE_TOLERANCE).
        assertEquals(BLOB_CENTRE, window.x + (window.width - 1) * 0.5, CENTRE_TOLERANCE);
        assertEquals(BLOB_CENTRE, window.y + (window.height - 1) * 0.5, CENTRE_TOLERANCE);
    }
}

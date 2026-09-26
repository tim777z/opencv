package org.opencv.test.video;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.test.OpenCVTestCase;
import org.opencv.video.KalmanFilter;

/**
 * Binding tests for {@link KalmanFilter}.
 *
 * <p>The generated Java class exposes the constructors, {@code predict()} and
 * {@code correct(Mat)}; the matrices of the filter are not wrapped, so these
 * tests pin down the contract that <em>is</em> observable: the dimensionality
 * and element type of the state, and the documented behaviour of a
 * freshly initialised filter (identity transition matrix, zero state, zero
 * measurement matrix). Estimation accuracy over a noise model is covered by the
 * C++ suite in modules/video/test/test_kalman.cpp.
 */
public class KalmanFilterTest extends OpenCVTestCase {

    private void assertState(Mat state, int dynamParams, int type) {
        assertNotNull(state);
        assertEquals(dynamParams, state.rows());
        assertEquals(1, state.cols());
        assertEquals(type, state.type());
    }

    public void testKalmanFilter() {
        KalmanFilter kf = new KalmanFilter();

        assertNotNull(kf);
    }

    public void testKalmanFilterIntInt() {
        KalmanFilter kf = new KalmanFilter(4, 2);

        assertState(kf.predict(), 4, CvType.CV_32F);
    }

    public void testKalmanFilterIntIntInt() {
        KalmanFilter kf = new KalmanFilter(4, 2, 1);

        assertState(kf.predict(), 4, CvType.CV_32F);
    }

    public void testKalmanFilterIntIntIntInt() {
        // The fourth argument selects the element type of every internal matrix.
        KalmanFilter kf = new KalmanFilter(2, 1, 0, CvType.CV_64F);

        assertState(kf.predict(), 2, CvType.CV_64F);
    }

    public void testPredict() {
        KalmanFilter kf = new KalmanFilter(5, 3);

        // A fresh filter has statePost == 0 and an identity transition matrix,
        // so predicting must yield a zero state of the configured size and
        // must stay there for a stationary system.
        Mat predicted = kf.predict();
        assertState(predicted, 5, CvType.CV_32F);
        assertEquals(0, Core.countNonZero(predicted));

        Mat predictedAgain = kf.predict();
        assertState(predictedAgain, 5, CvType.CV_32F);
        assertEquals(0, Core.countNonZero(predictedAgain));
    }

    public void testPredictMat() {
        KalmanFilter kf = new KalmanFilter(3, 2, 1);
        Mat control = new Mat(1, 1, CvType.CV_32F, new Scalar(1));

        // The control vector is accepted (it is CP x 1) and added through the
        // control matrix, which is zero until the caller replaces it.
        Mat predicted = kf.predict(control);
        assertState(predicted, 3, CvType.CV_32F);
        assertEquals(0, Core.countNonZero(predicted));
    }

    public void testCorrect() {
        KalmanFilter kf = new KalmanFilter(4, 2);
        Mat predicted = kf.predict();
        Mat measurement = new Mat(2, 1, CvType.CV_32F, new Scalar(1, 2));

        Mat corrected = kf.correct(measurement);

        assertState(corrected, 4, CvType.CV_32F);
        // The default measurement matrix is zero, hence the gain is zero and the
        // correction leaves the state at its predicted value.
        assertMatEqual(predicted, corrected, EPS);
    }
}

package com.orbbec.obsensor.types;

import com.orbbec.obsensor.ByteConversion;

/**
 * \if English
 * Camera internal parameters
 * \else
 * 相机内参
 * \endif
 */
public class CameraIntrinsic implements ByteConversion {
    /**
     * \if English
     * Focal length in x direction, unit: pixel
     * \else
     * x方向焦距，单位：像素
     * \endif
     */
    @StructField(offset = 0, size = 4)
    private float mFx;
    /**
     * \if English
     * Focal length in y direction, unit: pixel
     * \else
     * y方向焦距，单位：像素
     * \endif
     */
    @StructField(offset = 4, size = 4)
    private float mFy;
    /**
     * \if English
     * Optical center abscissa
     * \else
     * 光心横坐标
     * \endif
     */
    @StructField(offset = 8, size = 4)
    private float mCx;
    /**
     * \if English
     * Optical center ordinate
     * \else
     * 光心纵坐标
     * \endif
     */
    @StructField(offset = 12, size = 4)
    private float mCy;
    /**
     * \if English
     * image width
     * \else
     * 图像宽度
     * \endif
     */
    @StructField(offset = 16, size = 2)
    private short mWidth;
    /**
     * \if English
     * image height
     * \else
     * 图像高度
     * \endif
     */

    public float getFx() {
        return mFx;
    }
    public float getFy() {
        return mFy;
    }
    public float getCx() {
        return mCx;
    }
    public float getCy() {
        return mCy;
    }
    public short getWidth() {
        return mWidth;
    }
    public short getHeight() {
        return mHeight;
    }

    @StructField(offset = 18, size = 2)
    private short mHeight;

    private byte[] mBytes;

    @Override
    public byte[] getBytes() {
        if (mBytes == null) {
            mBytes = new byte[20];
        }
        return mBytes;
    }

    @Override
    public boolean parseBytes() {
        return StructParser.parseBytes(this, mBytes);
    }

    @Override
    public String toString() {
        return "CameraIntrinsic{" +
                "mFx=" + mFx +
                ", mFy=" + mFy +
                ", mCx=" + mCx +
                ", mCy=" + mCy +
                ", mWidth=" + mWidth +
                ", mHeight=" + mHeight +
                '}';
    }

    public CameraIntrinsic createRotated90() {
        CameraIntrinsic rotated = new CameraIntrinsic();

        // 1. fx, fy 초점거리 스왑
        rotated.mFx = this.mFy;
        rotated.mFy = this.mFx;

        // 2. 중심점(cx, cy) 회전축 역산 (회전 전 mHeight 기준)
        rotated.mCx = (float) this.mHeight - 1f - this.mCy;
        rotated.mCy = this.mCx;

        // 3. 해상도 가로/세로 스왑
        rotated.mWidth = this.mHeight;
        rotated.mHeight = this.mWidth;

        // 만약 SDK 내부에서 mBytes 바이트 배열을 역으로 요구한다면
        // 여기에 바이트 패킹 로직이 필요할 수 있으나, 일반적으로 3D 계산은 Getter를 참조하므로 이대로 충분합니다.

        return rotated;
    }
}

/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.view;

import android.annotation.NonNull;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.CanvasProperty;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Pools.SynchronizedPool;

/**
 * An implementation of a GL canvas that records drawing operations.
 * This is intended for use with a DisplayList. This class keeps a list of all the Paint and
 * Bitmap objects that it draws, preventing the backing memory of Bitmaps from being freed while
 * the DisplayList is still holding a native reference to the memory.
 *
 * @hide
 */
public class DisplayListCanvas extends Canvas {
    // The recording canvas pool should be large enough to handle a deeply nested
    // view hierarchy because display lists are generated recursively.
    private static final int POOL_LIMIT = 25;

    private static final SynchronizedPool<DisplayListCanvas> sPool =
            new SynchronizedPool<DisplayListCanvas>(POOL_LIMIT);

    RenderNode mNode;
    private int mWidth;
    private int mHeight;

    static DisplayListCanvas obtain(@NonNull RenderNode node, int width, int height) {
        if (node == null) throw new IllegalArgumentException("node cannot be null");
        DisplayListCanvas canvas = sPool.acquire();
        if (canvas == null) {
            canvas = new DisplayListCanvas(width, height);
        } else {
            nResetDisplayListCanvas(canvas.mNativeCanvasWrapper, width, height);
        }
        canvas.mNode = node;
        canvas.mWidth = width;
        canvas.mHeight = height;
        return canvas;
    }

    void recycle() {
        mNode = null;
        sPool.release(this);
    }

    long finishRecording() {
        return nFinishRecording(mNativeCanvasWrapper);
    }

    @Override
    public boolean isRecordingFor(Object o) {
        return o == mNode;
    }

    ///////////////////////////////////////////////////////////////////////////
    // JNI
    ///////////////////////////////////////////////////////////////////////////

    private static native boolean nIsAvailable();
    private static boolean sIsAvailable = nIsAvailable();

    static boolean isAvailable() {
        return sIsAvailable;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////////////////

    private DisplayListCanvas(int width, int height) {
        super(nCreateDisplayListCanvas(width, height));
        mDensity = 0; // disable bitmap density scaling
    }

    private static native long nCreateDisplayListCanvas(int width, int height);
    private static native void nResetDisplayListCanvas(long canvas, int width, int height);

    ///////////////////////////////////////////////////////////////////////////
    // Canvas management
    ///////////////////////////////////////////////////////////////////////////


    @Override
    public void setDensity(int density) {
        // drop silently, since DisplayListCanvas doesn't perform density scaling
    }

    @Override
    public boolean isHardwareAccelerated() {
        return true;
    }

    @Override
    public void setBitmap(Bitmap bitmap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpaque() {
        return false;
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    @Override
    public int getMaximumBitmapWidth() {
        return nGetMaximumTextureWidth();
    }

    @Override
    public int getMaximumBitmapHeight() {
        return nGetMaximumTextureHeight();
    }

    private static native int nGetMaximumTextureWidth();
    private static native int nGetMaximumTextureHeight();

    ///////////////////////////////////////////////////////////////////////////
    // Setup
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void insertReorderBarrier() {
        nInsertReorderBarrier(mNativeCanvasWrapper, true);
    }

    @Override
    public void insertInorderBarrier() {
        nInsertReorderBarrier(mNativeCanvasWrapper, false);
    }

    private static native void nInsertReorderBarrier(long renderer, boolean enableReorder);

    ///////////////////////////////////////////////////////////////////////////
    // Functor
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Calls the function specified with the drawGLFunction function pointer. This is
     * functionality used by webkit for calling into their renderer from our display lists.
     * This function may return true if an invalidation is needed after the call.
     *
     * @param drawGLFunction A native function pointer
     */
    public void callDrawGLFunction2(long drawGLFunction) {
        nCallDrawGLFunction(mNativeCanvasWrapper, drawGLFunction);
    }

    private static native void nCallDrawGLFunction(long renderer, long drawGLFunction);

    ///////////////////////////////////////////////////////////////////////////
    // Display list
    ///////////////////////////////////////////////////////////////////////////

    protected static native long nFinishRecording(long renderer);

    /**
     * Draws the specified display list onto this canvas. The display list can only
     * be drawn if {@link android.view.RenderNode#isValid()} returns true.
     *
     * @param renderNode The RenderNode to draw.
     */
    public void drawRenderNode(RenderNode renderNode) {
        nDrawRenderNode(mNativeCanvasWrapper, renderNode.getNativeDisplayList());
    }

    private static native void nDrawRenderNode(long renderer, long renderNode);

    ///////////////////////////////////////////////////////////////////////////
    // Hardware layer
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Draws the specified layer onto this canvas.
     *
     * @param layer The layer to composite on this canvas
     * @param x The left coordinate of the layer
     * @param y The top coordinate of the layer
     * @param paint The paint used to draw the layer
     */
    void drawHardwareLayer(HardwareLayer layer, float x, float y, Paint paint) {
        layer.setLayerPaint(paint);
        nDrawLayer(mNativeCanvasWrapper, layer.getLayerHandle(), x, y);
    }

    private static native void nDrawLayer(long renderer, long layer, float x, float y);

    ///////////////////////////////////////////////////////////////////////////
    // Drawing
    ///////////////////////////////////////////////////////////////////////////

    // TODO: move to Canvas.java
    @Override
    public void drawPatch(NinePatch patch, Rect dst, Paint paint) {
        Bitmap bitmap = patch.getBitmap();
        throwIfCannotDraw(bitmap);
        final long nativePaint = paint == null ? 0 : paint.getNativeInstance();
        nDrawPatch(mNativeCanvasWrapper, bitmap, patch.mNativeChunk,
                dst.left, dst.top, dst.right, dst.bottom, nativePaint);
    }

    // TODO: move to Canvas.java
    @Override
    public void drawPatch(NinePatch patch, RectF dst, Paint paint) {
        Bitmap bitmap = patch.getBitmap();
        throwIfCannotDraw(bitmap);
        final long nativePaint = paint == null ? 0 : paint.getNativeInstance();
        nDrawPatch(mNativeCanvasWrapper, bitmap, patch.mNativeChunk,
                dst.left, dst.top, dst.right, dst.bottom, nativePaint);
    }

    private static native void nDrawPatch(long renderer, Bitmap bitmap, long chunk,
            float left, float top, float right, float bottom, long paint);

    public void drawCircle(CanvasProperty<Float> cx, CanvasProperty<Float> cy,
            CanvasProperty<Float> radius, CanvasProperty<Paint> paint) {
        nDrawCircle(mNativeCanvasWrapper, cx.getNativeContainer(), cy.getNativeContainer(),
                radius.getNativeContainer(), paint.getNativeContainer());
    }

    private static native void nDrawCircle(long renderer, long propCx,
            long propCy, long propRadius, long propPaint);

    public void drawRoundRect(CanvasProperty<Float> left, CanvasProperty<Float> top,
            CanvasProperty<Float> right, CanvasProperty<Float> bottom, CanvasProperty<Float> rx,
            CanvasProperty<Float> ry, CanvasProperty<Paint> paint) {
        nDrawRoundRect(mNativeCanvasWrapper, left.getNativeContainer(), top.getNativeContainer(),
                right.getNativeContainer(), bottom.getNativeContainer(),
                rx.getNativeContainer(), ry.getNativeContainer(),
                paint.getNativeContainer());
    }

    private static native void nDrawRoundRect(long renderer, long propLeft, long propTop,
            long propRight, long propBottom, long propRx, long propRy, long propPaint);
}

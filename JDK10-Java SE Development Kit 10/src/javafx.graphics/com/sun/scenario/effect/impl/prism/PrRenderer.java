/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.sun.scenario.effect.impl.prism;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import com.sun.glass.ui.Screen;
import com.sun.prism.GraphicsPipeline;
import com.sun.prism.GraphicsPipeline.ShaderModel;
import com.sun.prism.RTTexture;
import com.sun.scenario.effect.FilterContext;
import com.sun.scenario.effect.impl.Renderer;

public abstract class PrRenderer extends Renderer {

    /**
     * Maintain a list of peers that are implemented using Prism, so that
     * we can do a fast check to see whether the given peer name is an
     * intrinsic one instead of relying on reflection to do the check.
     */
    private static final Set<String> intrinsicPeerNames;
    static {
        intrinsicPeerNames = new HashSet<String>(4);
        intrinsicPeerNames.add("Crop");
        intrinsicPeerNames.add("Flood");
        intrinsicPeerNames.add("Merge");
        intrinsicPeerNames.add("Reflection");
    }

    /**
     * Private constructor to prevent instantiation.
     */
    protected PrRenderer() {
    }

    public abstract PrDrawable createDrawable(RTTexture rtt);

    public static Renderer createRenderer(FilterContext fctx) {
        Object ref = fctx.getReferent();
        if (!(ref instanceof Screen)) {
            return null;
        }
        boolean isHW;
        if (((PrFilterContext) fctx).isForceSoftware()) {
            isHW = false;
        } else {
            GraphicsPipeline pipe = GraphicsPipeline.getPipeline();
            if (pipe == null) {
                return null;
            }
            isHW = pipe.supportsShaderModel(ShaderModel.SM3);
        }
        return createRenderer(fctx, isHW);
    }

    private static PrRenderer createRenderer(FilterContext fctx, boolean isHW) {
        String klassName = isHW ?
            Renderer.rootPkg + ".impl.prism.ps.PPSRenderer" :
            Renderer.rootPkg + ".impl.prism.sw.PSWRenderer";
        try {
            Class klass = Class.forName(klassName);
            Method m = klass.getMethod("createRenderer", new Class[] { FilterContext.class });
            return (PrRenderer)m.invoke(null, new Object[] { fctx });
        } catch (Throwable e) {}
        return null;
    }

    public static boolean isIntrinsicPeer(String name) {
        return intrinsicPeerNames.contains(name);
    }
}

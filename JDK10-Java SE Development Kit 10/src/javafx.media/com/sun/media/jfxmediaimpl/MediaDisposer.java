/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.media.jfxmediaimpl;

import com.sun.media.jfxmedia.logging.Logger;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This guy sits and waits for PhantomReferences to appear in it's queue and
 * invokes associated disposers when they do.
 */
public class MediaDisposer {
    /**
     * Common interface for objects that can be disposed of directly.
     */
    public static interface Disposable {
        /**
         * dispose() is called when an associated object is garbage collected
         * and no longer reachable. Note that the associated object cannot be
         * accessed by the time this method is invoked.
         */
        public void dispose();
    }

    /**
     * Common interface for objects that can dispose of other objects.
     */
    public static interface ResourceDisposer {
        /**
         * Dispose the provided resource.
         * @param resource the resource that needs to be disposed of.
         */
        public void disposeResource(Object resource);
    }

    /**
     * Register a resource to be dispose of when the referent becomes collected.
     * @param referent when this object is garbage collected, the resource will
     * be disposed
     * @param resource generic object to be disposed, this is passed as-is as an
     * argument to the disposer
     * @param disposer resource disposer class instance that will be used to
     * dispose the provided resource
     */
    public static void addResourceDisposer(Object referent, Object resource, ResourceDisposer disposer) {
        disposinator().implAddResourceDisposer(referent, resource, disposer);
    }

    /**
     * Remove ALL previously registered resource disposers associated with the
     * given resource. You use this when the resource will be otherwise safely
     * disposed of and no longer need to rely on this disposer to clean up.
     * @param resource the resource that was previously registered,
     * {@code equals()} is used to determine if it's the same object or not
     */
    public static void removeResourceDisposer(Object resource) {
        disposinator().implRemoveResourceDisposer(resource);
    }

    /**
     * Registers a disposable object that will be disposed when the referent is
     * garbage collected.
     * @param referent the object that the disposable object is associated with
     * @param disposable the object to be disposed when referent is collected
     */
    public static void addDisposable(Object referent, Disposable disposable) {
        disposinator().implAddDisposable(referent, disposable);
    }

    private final ReferenceQueue<Object> purgatory;
    private final Map<Reference,Disposable> disposers;

    private static MediaDisposer theDisposinator;
    private static synchronized MediaDisposer disposinator() {
        if (null == theDisposinator) {
            theDisposinator = new MediaDisposer();

            // start a background thread that blocks on purgatory and runs indefinitely
            Thread disposerThread = new Thread(
                    () -> {
                        theDisposinator.disposerLoop();
                    },
                    "Media Resource Disposer");
            disposerThread.setDaemon(true);
            disposerThread.start();
        }
        return theDisposinator;
    }

    private MediaDisposer() {
        purgatory = new ReferenceQueue();
        // disposers is accessed by multiple threads potentially simultaneously,
        // so make it synchronized
        disposers = new  HashMap<Reference,Disposable>();
    }

    private void disposerLoop() {
        // FIXME: make this interruptable?
        while (true) {
            try {
                Reference denizen = purgatory.remove();
                Disposable disposer;

                synchronized (disposers) {
                    disposer = disposers.remove(denizen);
                }

                denizen.clear();
                if (null != disposer) {
                    disposer.dispose();
                }
                denizen = null;
                disposer = null;
            } catch (InterruptedException ex) {
                if (Logger.canLog(Logger.DEBUG)) {
                    Logger.logMsg(Logger.DEBUG, MediaDisposer.class.getName(),
                            "disposerLoop", "Disposer loop interrupted, terminating");
                }
            }
        }
    }

    private void implAddResourceDisposer(Object referent, Object resource, ResourceDisposer disposer) {
        Reference denizen = new PhantomReference(referent, purgatory);
        synchronized (disposers) {
            disposers.put(denizen, new ResourceDisposerRecord(resource, disposer));
        }
    }

    private void implRemoveResourceDisposer(Object resource) {
        Reference resourceKey = null;

        synchronized (disposers) {
            for (Map.Entry<Reference, Disposable> entry : disposers.entrySet()) {
                Disposable disposer = entry.getValue();
                if (disposer instanceof ResourceDisposerRecord) {
                    ResourceDisposerRecord rd = (ResourceDisposerRecord)disposer;
                    if (rd.resource.equals(resource)) {
                        resourceKey = entry.getKey();
                        break; // no need to continue
                    }
                }
            }

            if (null != resourceKey) {
                disposers.remove(resourceKey);
            }
        }
    }

    private void implAddDisposable(Object referent, Disposable disposer) {
        Reference denizen = new PhantomReference(referent, purgatory);
        synchronized (disposers) {
            disposers.put(denizen, disposer);
        }
    }

    private static class ResourceDisposerRecord implements Disposable {
        Object resource;
        ResourceDisposer disposer;

        public ResourceDisposerRecord(Object resource, ResourceDisposer disposer) {
            this.resource = resource;
            this.disposer = disposer;
        }

        public void dispose() {
            disposer.disposeResource(resource);
        }
    }
}

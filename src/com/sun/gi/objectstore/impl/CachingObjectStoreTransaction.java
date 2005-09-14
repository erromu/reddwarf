/*
 * CachingObjectStoreTransaction.java
 *
 * Created on August 25, 2005, 4:51 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package com.sun.gi.objectstore.impl;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.objectstore.impl.CachingObjectStore.CachingObjectStoreLock;
import com.sun.gi.objectstore.impl.CachingObjectStore.CachingObjectStoreLockException;
import com.sun.gi.objectstore.impl.CachingObjectStoreCache;
import com.sun.gi.objectstore.impl.CachingObjectStoreCache.Entry;
import static com.sun.gi.objectstore.impl.CachingObjectStoreCache.*;

/**
 *
 * @author sundt2
 */
public class CachingObjectStoreTransaction implements Transaction {
    
    CachingObjectStoreCache cache;
    private CachingObjectStore cstore;
    private ObjectStore store;
    private Transaction trans;
    private HashSet<Entry> updateSet;
    
    /**
     * Creates a new instance of CachingObjectStoreTransaction
     */
    CachingObjectStoreTransaction(
            CachingObjectStore cstore, Transaction trans, int cacheSize) {
        this.cstore = cstore;
        this.store = cstore.getStore();
        this.trans = trans;
        this.cache = new CachingObjectStoreCache(cacheSize);
        this.updateSet = new HashSet<Entry>(16);
    }
    
    public long create(Serializable object, String name) {
        long id = cstore.nextObjectID();
        Entry cacheObj = cache.put(UPDATE_CREATE, id, name, object);
        synchronized (updateSet) {
            updateSet.add(cacheObj);
        }
        return id;
    }
    
    public void destroy(long objectID) {
        Entry cacheObj = cache.put(UPDATE_DESTROY, objectID, null, null);
        synchronized (updateSet) {
            updateSet.add(cacheObj);
        }
    }
    
    public Serializable peek(long objectID) {
        // read from transaction cache first, then global cache
        Entry cacheObj = cache.get(objectID);
        if (cacheObj == null) {
            cacheObj = cstore.cache.get(objectID);
        }
        if (cacheObj != null) {
            return cacheObj.sobj;
        } else {
            Serializable sobj = trans.peek(objectID);
            cache.put(UPDATE_NONE, objectID, null, sobj);
            return sobj;
        }
    }
    
    public Serializable lock(long objectID) throws DeadlockException {
        synchronized (this) {
            try {
                cstore.lock(this, objectID);
                
                Entry cacheObj = cache.get(objectID);
                if (cacheObj == null) {
                    cacheObj = cstore.cache.get(objectID);
                }
                if (cacheObj != null) {
                    return cacheObj.sobj;
                } else {
                    Serializable sobj = trans.lock(objectID);
                    cacheObj = cache.put(UPDATE_LOCK, objectID, null, sobj);
                    synchronized (updateSet) {
                        updateSet.add(cacheObj);
                    }
                    return sobj;
                }
            } catch (CachingObjectStoreLockException lockE) {
                lockE.printStackTrace();
                return null;
            }
        }
    }
    
    public long lookup(String name) {
        // read from local cache first, then global cache
        long id = cache.getID(name);
        if (id == -1) {
            id = cstore.cache.getID(name);
        }
        if (id != -1) {
            return id;
        } else {
            id = trans.lookup(name);
            if (id >= 0) {
                cache.put(id, name, null);
            }
            return id;
        }
    }
    
    public void abort() {
        synchronized (this) {
            cstore.unlockAll(this); // clear all locks
            trans.abort();
        }
    }
    
    public void commit() {
        synchronized (this) {
            synchronized (updateSet) {
                for (Entry cacheObj : updateSet) {
                    switch (cacheObj.updateMode) {
                        case UPDATE_CREATE:
                            trans.create(cacheObj.sobj, cacheObj.name);
                            break;
                        case UPDATE_DESTROY:
                            trans.destroy(cacheObj.id);
                            break;
                    }
                    
                }
                updateSet.clear();
                cstore.commit(this); // finish my transaction
            }
            trans.commit();
        }
    }
    
    public long getCurrentAppID() {
        return trans.getCurrentAppID();
    }
    
    public long lookupObject(Serializable sobj) {
        // read from local cache first, then global cache
        long id = cache.getID(sobj);
        if (id < 0) {
            id = cstore.cache.getID(sobj);
        }
        if (id < 0) {
            id = trans.lookupObject(sobj);
            if (id >= 0) {
                cache.put(UPDATE_NONE, id, null, sobj);
            }
        }
        return id;
    }
}

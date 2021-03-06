/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pig.backend.hadoop;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.util.StringUtils;
import org.apache.pig.impl.PigImplConstants;
import org.apache.pig.impl.util.UDFContext;

/**
 * Writable for DateTime values.
 */
public class DateTimeWritable implements WritableComparable {

    private static List<String> availableZoneIDs = null;
    private DateTime value = null;

    public DateTimeWritable() {

    }

    public DateTimeWritable(DateTime dt) {
        value = dt;
    }

    public void readFields(DataInput in) throws IOException {
        retrieveAvailableZoneList();

        long instant = in.readLong();
        int offsetInMillis = in.readInt();
        int zoneListPos = in.readInt();

        DateTimeZone timeZone = null;
        if (zoneListPos != -1){
            timeZone = DateTimeZone.forID(availableZoneIDs.get(zoneListPos));
        } else  {
            timeZone = DateTimeZone.forOffsetMillis(offsetInMillis);
        }

        value = new DateTime(instant, timeZone);
    }

    public void write(DataOutput out) throws IOException {
        retrieveAvailableZoneList();

        String zoneId = value.getZone().getID();
        int offsetInMillis = value.getZone().getOffset(0L);
        int zoneListPos = availableZoneIDs.indexOf(zoneId);

        out.writeLong(value.getMillis());
        out.writeInt(offsetInMillis);
        out.writeInt(zoneListPos);
    }

    public void set(DateTime dt) {
        value = dt;
    }

    public DateTime get() {
        return value;
    }

    private void retrieveAvailableZoneList() throws IOException {
        if (availableZoneIDs != null){
            return;
        }
        Properties props = UDFContext.getUDFContext().getUDFProperties(PigImplConstants.PIG_DATETIME_ZONES_LIST.getClass());
        Collection<String> zoneList = StringUtils.getStringCollection(props.getProperty(PigImplConstants.PIG_DATETIME_ZONES_LIST));
        if (zoneList == null || zoneList.size() == 0){
            throw new IOException("Datetime zone information not set");
        }
        availableZoneIDs = new ArrayList<>(zoneList);
    }

    public static void setupAvailableZoneIds() {
        TreeSet<String> sortedZoneIDs = new TreeSet<>(DateTimeZone.getAvailableIDs());
        Properties props = UDFContext.getUDFContext().getUDFProperties(
                PigImplConstants.PIG_DATETIME_ZONES_LIST.getClass());
        props.setProperty(PigImplConstants.PIG_DATETIME_ZONES_LIST,  StringUtils.arrayToString(
                sortedZoneIDs.toArray(new String[0])));
    }

    /**
     * Returns true iff <code>o</code> is a DateTimeWritable with the same
     * value.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DateTimeWritable)) {
            return false;
        }
        DateTimeWritable other = (DateTimeWritable) o;
        return this.value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    public int compareTo(Object o) {
        DateTimeWritable other = (DateTimeWritable) o;
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toString();
    }

    /** 
     * A Comparator optimized for DateTimeWritable.
     */
    public static class Comparator extends WritableComparator {
        public Comparator() {
            super(DateTimeWritable.class);
        }

        @Override
        public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
            DateTime thisValue = new DateTime(readLong(b1, s1));
            DateTime thatValue = new DateTime(readLong(b2, s2));
            return thisValue.compareTo(thatValue);
        }
    }

    static { // register this comparator
        WritableComparator.define(DateTimeWritable.class, new Comparator());
    }
}

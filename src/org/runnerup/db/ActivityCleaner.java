/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.runnerup.db;

import java.util.ArrayList;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;

public class ActivityCleaner implements org.runnerup.util.Constants {

	/**
	 * recompute laps aggregates based on locations
	 */
	private static void recomputeLaps(SQLiteDatabase db, long activityId) {
		final String[] cols = new String[] { 
				DB.LAP.LAP
		};

		ArrayList<Long> laps = new ArrayList<Long>();
		Cursor c = db.query(DB.LOCATION.LAP, cols, "activity_id == " + activityId,
				null, null, null, "_id", null);
		if (c.moveToFirst()) {
			do {
				laps.add(c.getLong(0));
			} while (c.moveToNext());
		}
		c.close();
		
		for (long lap : laps) {
			recomputeLap(db, activityId, lap);
		}
	}

	/**
	 * recompute a lap aggregate based on locations
	 */
	private static void recomputeLap(SQLiteDatabase db, long activityId, long lap) {
		long sum_time = 0;
		double sum_distance = 0;
		
		final String[] cols = new String[] { 
				DB.LOCATION.TIME,
				DB.LOCATION.LATITUDE,
				DB.LOCATION.LONGITUDE,
				DB.LOCATION.TYPE,
				"_id" };

		Cursor c = db.query(DB.LOCATION.TABLE, cols, "activity_id == " + activityId + " and lap == " + lap,
				null, null, null, "_id", null);
		if (c.moveToFirst()) {
			Location lastLocation = null;
			do {
				Location l = new Location("Dill poh");
				l.setTime(c.getLong(0));
				l.setLatitude(c.getDouble(1));
				l.setLongitude(c.getDouble(2));
				l.setProvider("" + c.getLong(3));

				int type = c.getInt(3);
				switch (type) {
				case DB.LOCATION.TYPE_START:
				case DB.LOCATION.TYPE_RESUME:
					lastLocation = l;
					break;
				case DB.LOCATION.TYPE_END:
				case DB.LOCATION.TYPE_PAUSE:
				case DB.LOCATION.TYPE_GPS:
					if (lastLocation == null) {
						lastLocation = l;
						break;
					}

					sum_distance += l.distanceTo(lastLocation);
					sum_time += l.getTime() - lastLocation.getTime();
					lastLocation = l;
					break;
				}
			} while (c.moveToNext());
		}
		c.close();

		ContentValues tmp = new ContentValues();
		tmp.put(DB.LAP.DISTANCE, sum_distance);
		tmp.put(DB.LAP.TIME, (sum_time / 1000));
		db.update(DB.LAP.TABLE, tmp, "activity_id == " + activityId + " and lap == " + lap, null);
	}

	/**
	 * recompute an activity summary based on laps
	 */
	private static void recomputeSummary(SQLiteDatabase db, long activityId) {
		long sum_time = 0;
		double sum_distance = 0;
		
		final String[] cols = new String[] { 
				DB.LAP.DISTANCE,
				DB.LAP.TIME
		};
		Cursor c = db.query(DB.LAP.TABLE, cols, "activity_id == " + activityId,
				null, null, null, "_id", null);
		if (c.moveToFirst()) {
			do {
				sum_distance += c.getDouble(0);
				sum_time += c.getLong(1);
			} while (c.moveToNext());
		}
		c.close();

		ContentValues tmp = new ContentValues();
		tmp.put(DB.ACTIVITY.DISTANCE, sum_distance);
		tmp.put(DB.ACTIVITY.TIME, sum_time);
		db.update(DB.ACTIVITY.TABLE, tmp, "_id == " + activityId, null);
	}

	public static void recompute(SQLiteDatabase db, long activityId) {
		recomputeLaps(db, activityId);
		recomputeSummary(db, activityId);
	}
}

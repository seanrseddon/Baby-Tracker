const express = require('express');
const cors = require('cors');
const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const fs = require('fs');
const https = require('https');

let currentDbInstanceId = '';

const app = express();
const PORT = process.env.PORT || 3000;

// Enable CORS and JSON parsing with custom limit for large CSV imports
app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ limit: '50mb', extended: true }));

// Ensure data directory exists for persistent SQLite database on Docker/Unraid
const dataDir = process.env.DATA_DIR || path.join(__dirname, 'data');
if (!fs.existsSync(dataDir)) {
    fs.mkdirSync(dataDir, { recursive: true });
}
const dbPath = path.join(dataDir, 'baby_tracker.db');

const db = new sqlite3.Database(dbPath, (err) => {
    if (err) {
        console.error('Failed to connect to SQLite database:', err.message);
    } else {
        console.log('Connected to local SQLite database at', dbPath);
        createTables();
    }
});

// Initialize database schema
function createTables() {
    db.run(`
        CREATE TABLE IF NOT EXISTS activities (
            id TEXT PRIMARY KEY,
            type TEXT NOT NULL,
            babyName TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            detailsJson TEXT NOT NULL,
            notes TEXT,
            updatedAt INTEGER NOT NULL,
            isDeleted INTEGER DEFAULT 0
        )
    `, (err) => {
        if (err) {
            console.error('Error creating activities table:', err.message);
        } else {
            console.log('Activities table verified/created.');
        }
    });

    db.run(`
        CREATE TABLE IF NOT EXISTS profile (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
    `, (err) => {
        if (err) {
            console.error('Error creating profile table:', err.message);
        } else {
            console.log('Profile table verified/created.');
            // Seed defaults if not present
            db.get("SELECT value FROM profile WHERE key = 'babyName'", [], (err, row) => {
                if (!row) {
                    db.run("INSERT INTO profile (key, value) VALUES ('babyName', 'Baby')");
                }
            });
            db.get("SELECT value FROM profile WHERE key = 'babyDob'", [], (err, row) => {
                if (!row) {
                    db.run("INSERT INTO profile (key, value) VALUES ('babyDob', '0')");
                }
            });
            db.get("SELECT value FROM profile WHERE key = 'dbInstanceId'", [], (err, row) => {
                if (row) {
                    currentDbInstanceId = row.value;
                    console.log('Loaded database instance ID:', currentDbInstanceId);
                } else {
                    const crypto = require('crypto');
                    currentDbInstanceId = crypto.randomUUID();
                    db.run("INSERT OR REPLACE INTO profile (key, value) VALUES ('dbInstanceId', ?)", [currentDbInstanceId], (err2) => {
                        if (err2) {
                            console.error('Error saving dbInstanceId:', err2.message);
                        } else {
                            console.log('Generated new database instance ID:', currentDbInstanceId);
                        }
                    });
                }
            });
        }
    });
}

// REST API - Sync bi-directional
app.post('/api/activities/sync', (req, joinRes) => {
    const { lastSyncTime, clientActivities } = req.body;
    const clientLogs = clientActivities || [];
    const lastSync = lastSyncTime || 0;

    if (clientLogs.length === 0) {
        // Simple case: fetch updates only, no client logs to process
        db.all(
            "SELECT * FROM activities WHERE updatedAt > ?",
            [lastSync],
            (err, rows) => {
                if (err) {
                    console.error('Sync query error:', err.message);
                    return joinRes.status(500).json({ error: 'Database sync error' });
                }

                const serverUpdates = rows.map(r => ({
                    id: r.id,
                    type: r.type,
                    babyName: r.babyName,
                    timestamp: r.timestamp,
                    detailsJson: r.detailsJson,
                    notes: r.notes,
                    updatedAt: r.updatedAt,
                    isDeleted: r.isDeleted === 1
                }));

                const newSyncTime = Date.now();
                return joinRes.json({
                    serverSyncTime: newSyncTime,
                    updates: serverUpdates,
                    dbInstanceId: currentDbInstanceId
                });
            }
        );
        return;
    }

    // Wrap in SQLite transaction for high performance (processes thousands of logs in milliseconds)
    db.serialize(() => {
        db.run("BEGIN TRANSACTION");

        const insertStmt = db.prepare(`
            INSERT INTO activities (id, type, babyName, timestamp, detailsJson, notes, updatedAt, isDeleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                type = excluded.type,
                babyName = excluded.babyName,
                timestamp = excluded.timestamp,
                detailsJson = excluded.detailsJson,
                notes = excluded.notes,
                updatedAt = excluded.updatedAt,
                isDeleted = excluded.isDeleted
            WHERE excluded.updatedAt > activities.updatedAt
        `);

        let hasError = false;
        let errMsg = '';

        for (const log of clientLogs) {
            insertStmt.run(
                log.id,
                log.type,
                log.babyName || 'Baby',
                log.timestamp,
                log.detailsJson,
                log.notes || '',
                log.updatedAt,
                log.isDeleted ? 1 : 0,
                (err) => {
                    if (err) {
                        hasError = true;
                        errMsg = err.message;
                    }
                }
            );
            if (hasError) break;
        }

        insertStmt.finalize((err) => {
            if (err || hasError) {
                db.run("ROLLBACK", () => {
                    console.error('Sync transaction rollback:', err?.message || errMsg);
                    return joinRes.status(500).json({ error: 'Database sync transaction failed' });
                });
                return;
            }

            db.run("COMMIT", (commitErr) => {
                if (commitErr) {
                    db.run("ROLLBACK");
                    console.error('Sync transaction commit failed:', commitErr.message);
                    return joinRes.status(500).json({ error: 'Database sync commit failed' });
                }

                // Query all activities updated after lastSyncTime to send back to client
                db.all(
                    "SELECT * FROM activities WHERE updatedAt > ?",
                    [lastSync],
                    (err, rows) => {
                        if (err) {
                            console.error('Sync query error:', err.message);
                            return joinRes.status(500).json({ error: 'Database sync query error' });
                        }

                        const serverUpdates = rows.map(r => ({
                            id: r.id,
                            type: r.type,
                            babyName: r.babyName,
                            timestamp: r.timestamp,
                            detailsJson: r.detailsJson,
                            notes: r.notes,
                            updatedAt: r.updatedAt,
                            isDeleted: r.isDeleted === 1
                        }));

                        const newSyncTime = Date.now();
                        joinRes.json({
                            serverSyncTime: newSyncTime,
                            updates: serverUpdates,
                            dbInstanceId: currentDbInstanceId
                        });
                    }
                );
            });
        });
    });
});

// REST API - Get all active (not deleted) activities for general queries
app.get('/api/activities', (req, res) => {
    db.all("SELECT * FROM activities WHERE isDeleted = 0 ORDER BY timestamp DESC", [], (err, rows) => {
        if (err) {
            return res.status(500).json({ error: err.message });
        }
        res.json(rows.map(r => ({
            ...r,
            isDeleted: r.isDeleted === 1
        })));
    });
});

// REST API - Create single activity
app.post('/api/activities', (req, res) => {
    const { id, type, babyName, timestamp, detailsJson, notes } = req.body;
    if (!id || !type || !babyName || !timestamp || !detailsJson) {
        return res.status(400).json({ error: 'Missing required fields' });
    }

    const updatedAt = Date.now();
    db.run(
        `INSERT INTO activities (id, type, babyName, timestamp, detailsJson, notes, updatedAt, isDeleted)
         VALUES (?, ?, ?, ?, ?, ?, ?, 0)
         ON CONFLICT(id) DO UPDATE SET
            type = excluded.type,
            babyName = excluded.babyName,
            timestamp = excluded.timestamp,
            detailsJson = excluded.detailsJson,
            notes = excluded.notes,
            updatedAt = excluded.updatedAt,
            isDeleted = 0`,
        [id, type, babyName, timestamp, detailsJson, notes || '', updatedAt],
        function(err) {
            if (err) {
                return res.status(500).json({ error: err.message });
            }
            res.json({ success: true, id, updatedAt });
        }
    );
});

// REST API - Create bulk activities (useful for importing CSV)
app.post('/api/activities/bulk', (req, res) => {
    const { activities } = req.body;
    if (!Array.isArray(activities)) {
        return res.status(400).json({ error: 'Expected activities array' });
    }

    db.serialize(() => {
        db.run("BEGIN TRANSACTION");
        const stmt = db.prepare(`
            INSERT INTO activities (id, type, babyName, timestamp, detailsJson, notes, updatedAt, isDeleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
               type = excluded.type,
               babyName = excluded.babyName,
               timestamp = excluded.timestamp,
               detailsJson = excluded.detailsJson,
               notes = excluded.notes,
               updatedAt = excluded.updatedAt,
               isDeleted = excluded.isDeleted
        `);

        let hasError = false;
        let errMsg = '';

        activities.forEach(act => {
            const isDel = act.isDeleted === true || act.isDeleted === 1 || act.isDeleted === 'true' ? 1 : 0;
            stmt.run(
                [
                    act.id,
                    act.type,
                    act.babyName,
                    parseInt(act.timestamp),
                    act.detailsJson,
                    act.notes || '',
                    parseInt(act.updatedAt || Date.now()),
                    isDel
                ],
                (err) => {
                    if (err) {
                        hasError = true;
                        errMsg = err.message;
                    }
                }
            );
        });

        stmt.finalize((err) => {
            if (err || hasError) {
                db.run("ROLLBACK");
                return res.status(500).json({ error: err?.message || errMsg || 'Failed to bulk insert' });
            }
            db.run("COMMIT");
            res.json({ success: true, count: activities.length });
        });
    });
});

// REST API - Update single activity via PUT
app.put('/api/activities/:id', (req, res) => {
    const { id } = req.params;
    const { type, babyName, timestamp, detailsJson, notes } = req.body;
    if (!type || !babyName || !timestamp || !detailsJson) {
        return res.status(400).json({ error: 'Missing required fields' });
    }

    const updatedAt = Date.now();
    db.run(
        `UPDATE activities SET type = ?, babyName = ?, timestamp = ?, detailsJson = ?, notes = ?, updatedAt = ?, isDeleted = 0 WHERE id = ?`,
        [type, babyName, timestamp, detailsJson, notes || '', updatedAt, id],
        function(err) {
            if (err) {
                return res.status(500).json({ error: err.message });
            }
            res.json({ success: true, id, updatedAt });
        }
    );
});

// REST API - Delete single activity
app.delete('/api/activities/:id', (req, res) => {
    const { id } = req.params;
    const updatedAt = Date.now();
    db.run(
        `UPDATE activities SET isDeleted = 1, updatedAt = ? WHERE id = ?`,
        [updatedAt, id],
        function(err) {
            if (err) {
                return res.status(500).json({ error: err.message });
            }
            res.json({ success: true, id, updatedAt });
        }
    );
});

// REST API - Delete ALL activities (wipe data)
app.delete('/api/activities', (req, res) => {
    db.run("DELETE FROM activities", [], function(err) {
        if (err) {
            return res.status(500).json({ error: err.message });
        }
        res.json({ success: true, message: 'All activities erased successfully', count: this.changes });
    });
});

// Helper function to call Gemini API
function callGemini(apiKey, prompt) {
    return new Promise((resolve, reject) => {
        const payload = JSON.stringify({
            contents: [{
                parts: [{ text: prompt }]
            }]
        });

        const options = {
            hostname: 'generativelanguage.googleapis.com',
            port: 443,
            path: `/v1beta/models/gemini-3.5-flash:generateContent?key=${apiKey}`,
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(payload)
            }
        };

        const req = https.request(options, (res) => {
            let data = '';
            res.on('data', (chunk) => { data += chunk; });
            res.on('end', () => {
                try {
                    const parsed = JSON.parse(data);
                    const text = parsed.candidates?.[0]?.content?.parts?.[0]?.text;
                    if (text) {
                        resolve(text);
                    } else {
                        reject(new Error('Invalid response from Gemini API: ' + data));
                    }
                } catch (e) {
                    reject(e);
                }
            });
        });

        req.on('error', (e) => {
            reject(e);
        });

        req.write(payload);
        req.end();
    });
}

// REST API - GET Profile
app.get('/api/profile', (req, res) => {
    db.all("SELECT * FROM profile", [], (err, rows) => {
        if (err) {
            return res.status(500).json({ error: err.message });
        }
        const profile = {};
        rows.forEach(r => {
            profile[r.key] = r.value;
        });
        res.json({
            babyName: profile.babyName || 'Baby',
            babyDob: parseInt(profile.babyDob || '0')
        });
    });
});

// REST API - GET Sleep Timer status
app.get('/api/sleep-timer', (req, res) => {
    db.get("SELECT value FROM profile WHERE key = 'sleepTimerStart'", [], (err, row) => {
        if (err) {
            return res.status(500).json({ error: err.message });
        }
        res.json({ startTime: row ? parseInt(row.value) : null });
    });
});

// REST API - POST start active sleep timer
app.post('/api/sleep-timer', (req, res) => {
    const { startTime } = req.body;
    if (!startTime) {
        return res.status(400).json({ error: 'Missing startTime parameter' });
    }
    db.run(
        "INSERT OR REPLACE INTO profile (key, value) VALUES ('sleepTimerStart', ?)",
        [startTime.toString()],
        function(err) {
            if (err) {
                return res.status(500).json({ error: err.message });
            }
            res.json({ success: true, startTime });
        }
    );
});

// REST API - DELETE stop/cancel sleep timer
app.delete('/api/sleep-timer', (req, res) => {
    db.run(
        "DELETE FROM profile WHERE key = 'sleepTimerStart'",
        [],
        function(err) {
            if (err) {
                return res.status(500).json({ error: err.message });
            }
            res.json({ success: true });
        }
    );
});

// REST API - POST Profile
app.post('/api/profile', (req, res) => {
    const { babyName, babyDob } = req.body;
    db.serialize(() => {
        const stmt = db.prepare("INSERT OR REPLACE INTO profile (key, value) VALUES (?, ?)");
        let count = 0;
        let hasError = false;

        const onDone = (err) => {
            if (err) hasError = true;
            count++;
            if (count === ( (babyName !== undefined ? 1 : 0) + (babyDob !== undefined ? 1 : 0) )) {
                stmt.finalize((finalErr) => {
                    if (hasError || finalErr) {
                        return res.status(500).json({ error: err?.message || finalErr?.message || 'Failed to save profile' });
                    }
                    res.json({ success: true });
                });
            }
        };

        if (babyName !== undefined) {
            stmt.run('babyName', babyName.toString(), onDone);
        }
        if (babyDob !== undefined) {
            stmt.run('babyDob', babyDob.toString(), onDone);
        }
        
        if (babyName === undefined && babyDob === undefined) {
            stmt.finalize();
            res.json({ success: true });
        }
    });
});

// REST API - POST Sleep Analysis via Gemini
app.post('/api/analyze-sleep', (req, res) => {
    const apiKey = req.headers['x-gemini-api-key'] || process.env.GEMINI_API_KEY;
    if (!apiKey || apiKey === 'MY_GEMINI_API_KEY' || apiKey.trim() === '') {
        return res.status(400).json({ error: 'Gemini API key is not configured. Please enter your Gemini API key in settings or set GEMINI_API_KEY on the server.' });
    }

    db.all("SELECT * FROM profile", [], (err, profileRows) => {
        if (err) {
            return res.status(500).json({ error: err.message });
        }
        const profile = {};
        profileRows.forEach(r => {
            profile[r.key] = r.value;
        });

        const babyName = profile.babyName || 'Baby';
        const babyDob = parseInt(profile.babyDob || '0');

        if (babyDob <= 0) {
            return res.status(400).json({ error: "Please configure your baby's Date of Birth first." });
        }

        const diffMs = Date.now() - babyDob;
        const days = Math.floor(diffMs / (1000 * 60 * 60 * 24));
        const months = Math.floor(days / 30);
        const weeks = Math.floor(days / 7);
        let ageStr = '';
        if (months > 0) {
            ageStr = `${months} months`;
        } else if (weeks > 0) {
            ageStr = `${weeks} weeks`;
        } else {
            ageStr = `${days} days`;
        }

        db.all(
            "SELECT * FROM activities WHERE type = 'SLEEP' AND isDeleted = 0 ORDER BY timestamp DESC LIMIT 20",
            [],
            async (err, logs) => {
                if (err) {
                    return res.status(500).json({ error: err.message });
                }

                let sleepLogsStr = '';
                if (logs.length === 0) {
                    sleepLogsStr = 'No sleep history recorded yet.';
                } else {
                    sleepLogsStr = logs.map(log => {
                        const dateStr = new Date(log.timestamp).toISOString().replace('T', ' ').substring(0, 16);
                        let duration = 0;
                        try {
                            const details = JSON.parse(log.detailsJson);
                            duration = details.durationMinutes || 0;
                        } catch (e) {}
                        return `- Date: ${dateStr}, Duration: ${duration} mins, Notes: ${log.notes || ''}`;
                    }).join('\n');
                }

                const prompt = `
You are a professional pediatric sleep consultant.
Analyze the sleep log for a baby named ${babyName} who is ${ageStr} old.

Here is their recent sleep log history (newest first):
${sleepLogsStr}

Based on their age of ${ageStr} and recent sleep patterns:
1. Evaluate if their sleep duration and frequency align with healthy targets for their developmental stage (citing global pediatric nap and wake window standards).
2. Estimate their average "wake window" (awake time between naps) and identify any overtiredness patterns.
3. Formulate clear, actionable recommendations for when they should go down for their next nap(s) or bedtime today.
4. Provide 2-3 specific, practical tips for optimizing their sleeping environment or routine.

Please structure your response beautifully with Markdown:
- Use bold titles for sections like: **Developmental Sleep Evaluation**, **Estimated Wake Window**, **Next Nap Target & Bedtime**, **Practical Sleep Tips**.
- Keep it clear, friendly, and practical. No verbose introduction.
`.trim();

                try {
                    const aiResponse = await callGemini(apiKey, prompt);
                    res.json({ recommendation: aiResponse });
                } catch (apiErr) {
                    console.error('Gemini API call failed:', apiErr);
                    res.status(500).json({ error: `Gemini analysis failed: ${apiErr.message}` });
                }
            }
        );
    });
});

// Serve frontend web dashboard
app.use(express.static(path.join(__dirname, 'public')));

// Fallback to web interface
app.get('*', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Start listening
app.listen(PORT, '0.0.0.0', () => {
    console.log(`Baby Tracker local backend running on port ${PORT}`);
    console.log(`Web Dashboard accessible locally at http://localhost:${PORT}`);
});

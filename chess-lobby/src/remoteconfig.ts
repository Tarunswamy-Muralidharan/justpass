// Firebase Remote Config REST API client.
//
// Remote Config doesn't expose per-parameter update endpoints — you GET the
// full template (with ETag), mutate locally, then PUT back with
// `If-Match: <etag>` for optimistic concurrency. If another writer publishes
// in between, we get 409 and have to retry.
//
// Doc: https://firebase.google.com/docs/reference/remote-config/rest

const REMOTE_CONFIG_SCOPE =
  "https://www.googleapis.com/auth/firebase.remoteconfig";

interface RemoteConfigParameter {
  defaultValue?: { value: string };
  valueType?: "STRING" | "BOOLEAN" | "NUMBER" | "JSON";
  description?: string;
  conditionalValues?: Record<string, { value: string }>;
}

interface RemoteConfigTemplate {
  parameters?: Record<string, RemoteConfigParameter>;
  conditions?: unknown[];
  parameterGroups?: Record<string, unknown>;
  version?: { versionNumber?: string };
  etag?: string;
}

export interface AnnouncementUpdate {
  active: boolean;
  id: string;
  title: string;
  message: string;
}

function templateUrl(projectId: string): string {
  return `https://firebaseremoteconfig.googleapis.com/v1/projects/${projectId}/remoteConfig`;
}

async function fetchTemplate(
  accessToken: string,
  projectId: string,
): Promise<{ template: RemoteConfigTemplate; etag: string }> {
  const res = await fetch(templateUrl(projectId), {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Accept-Encoding": "gzip",
    },
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Remote Config GET failed: ${res.status} ${body}`);
  }
  const template = (await res.json()) as RemoteConfigTemplate;
  const etag = res.headers.get("ETag") || "";
  if (!etag) {
    throw new Error("Remote Config GET returned no ETag");
  }
  return { template, etag };
}

async function publishTemplate(
  accessToken: string,
  projectId: string,
  template: RemoteConfigTemplate,
  etag: string,
): Promise<void> {
  const res = await fetch(templateUrl(projectId), {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json; UTF-8",
      "If-Match": etag,
    },
    body: JSON.stringify(template),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Remote Config PUT failed: ${res.status} ${body}`);
  }
}

/**
 * Updates the four announcement_* keys in Remote Config in one transactional
 * PUT. Falls back through up to 3 ETag retries if a concurrent publish
 * happens (rare for a single-admin app).
 */
export async function updateAnnouncement(
  accessToken: string,
  projectId: string,
  update: AnnouncementUpdate,
): Promise<void> {
  for (let attempt = 0; attempt < 3; attempt++) {
    const { template, etag } = await fetchTemplate(accessToken, projectId);
    template.parameters = template.parameters ?? {};

    template.parameters["announcement_active"] = {
      ...template.parameters["announcement_active"],
      defaultValue: { value: update.active ? "true" : "false" },
      valueType: "BOOLEAN",
    };
    template.parameters["announcement_id"] = {
      ...template.parameters["announcement_id"],
      defaultValue: { value: update.id },
      valueType: "STRING",
    };
    template.parameters["announcement_title"] = {
      ...template.parameters["announcement_title"],
      defaultValue: { value: update.title },
      valueType: "STRING",
    };
    template.parameters["announcement_message"] = {
      ...template.parameters["announcement_message"],
      defaultValue: { value: update.message },
      valueType: "STRING",
    };

    try {
      await publishTemplate(accessToken, projectId, template, etag);
      return;
    } catch (e) {
      // Retry only on 409/ETag mismatch. Any other error is fatal.
      const msg = e instanceof Error ? e.message : String(e);
      if (!/^Remote Config PUT failed: 4(09|12)/.test(msg) || attempt === 2) {
        throw e;
      }
      // small backoff before refetch + retry
      await new Promise((r) => setTimeout(r, 100 * (attempt + 1)));
    }
  }
}

export { REMOTE_CONFIG_SCOPE };

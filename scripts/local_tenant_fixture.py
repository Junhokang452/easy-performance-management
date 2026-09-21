"""Synthetic JWT fixture for an existing actor in tenant B; local demo only."""
import base64
import hashlib
import hmac
import json
from pathlib import Path


def tenant_b_headers(token):
    secrets = dict(line.split('=', 1) for line in (Path(__file__).resolve().parents[1] / '.local-demo/secrets.env').read_text().splitlines() if '=' in line)
    header, payload, _ = token.split('.')
    claims = json.loads(base64.urlsafe_b64decode(payload + '=' * (-len(payload) % 4)))
    claims.update(sub='019ed004-0000-7000-8000-000000000002', tid='00000000-0000-0000-0000-000000000002')
    payload = base64.urlsafe_b64encode(json.dumps(claims, separators=(',', ':')).encode()).rstrip(b'=').decode()
    content = header + '.' + payload
    signature = base64.urlsafe_b64encode(hmac.new(secrets['PERFORMANCE_DEMO_JWT_SECRET'].encode(), content.encode(), hashlib.sha512).digest()).rstrip(b'=').decode()
    return {'Authorization': 'Bearer ' + content + '.' + signature}

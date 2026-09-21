import assert from 'node:assert/strict';
import test from 'node:test';

import {
  distributionTotal,
  isValidDistribution,
  isValidGoalDraft,
  nextWorkflowStatus,
} from '../src/features/evaluation-workspace/workflowRules.ts';

test('keeps calibration as an explicit confirm/publish/close stage', () => {
  assert.equal(nextWorkflowStatus('MANAGER_REVIEW'), 'CALIBRATION');
  assert.equal(nextWorkflowStatus('CALIBRATION'), null);
  assert.equal(nextWorkflowStatus('FINALIZED'), null);
});

test('validates a meaningful goal title and percentage weight', () => {
  assert.equal(isValidGoalDraft('Increase renewal rate', 30), true);
  assert.equal(isValidGoalDraft('   ', 30), false);
  assert.equal(isValidGoalDraft('Increase renewal rate', 0), false);
  assert.equal(isValidGoalDraft('Increase renewal rate', 101), false);
  assert.equal(isValidGoalDraft('Increase renewal rate', Number.NaN), false);
});

test('requires the calibration target distribution to total exactly 100 percent', () => {
  const valid = { S: 0.1, A: 0.2, B: 0.4, C: 0.2, D: 0.1 };
  assert.ok(Math.abs(distributionTotal(valid) - 1) < 0.0001);
  assert.equal(isValidDistribution(valid), true);
  assert.equal(isValidDistribution({ ...valid, D: 0.2 }), false);
  assert.equal(isValidDistribution({ ...valid, S: -0.1, D: 0.3 }), false);
});

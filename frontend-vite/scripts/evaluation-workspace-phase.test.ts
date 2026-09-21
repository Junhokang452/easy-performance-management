import assert from 'node:assert/strict';
import test from 'node:test';

import {
  evaluationWorkspacePhases,
  getPhaseGate,
} from '../src/features/evaluation-workspace/phaseMap.ts';

test('maps an active cycle to an actionable setup phase and explains the next gate', () => {
  const setup = getPhaseGate('ACTIVE');
  const goals = getPhaseGate('GOAL_SETTING');

  assert.equal(setup.key, 'setup');
  assert.equal(setup.state, 'active');
  assert.equal(goals.key, 'goals');
  assert.equal(goals.state, 'active');
  assert.equal(evaluationWorkspacePhases.at(-1)?.key, 'closure');
});

test('keeps future workflow stages locked until their cycle status is reached', () => {
  assert.equal(getPhaseGate('PLANNED').state, 'blocked');
  assert.equal(getPhaseGate('MID_REVIEW').key, 'checkIn');
  assert.equal(getPhaseGate('FINALIZED').key, 'closure');
  assert.equal(getPhaseGate('FINALIZED').state, 'complete');
});

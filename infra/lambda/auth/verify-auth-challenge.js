exports.handler = async (event) => {
  const expected = event.request.privateChallengeParameters?.answer;
  event.response.answerCorrect = Boolean(expected) && event.request.challengeAnswer === expected;
  return event;
};

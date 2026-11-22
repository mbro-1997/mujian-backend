#!/usr/bin/env python3
"""Index observed responses separately from static reserved branches and external waits."""
import csv, json, os, re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
evidence = Path(os.environ['MJ_EVIDENCE_DIR'])
contract = json.loads((root / 'src/main/resources/contracts/d2a.openapi.json').read_text())
matrix = list(csv.DictReader((evidence / 'operation-http-reason-matrix.csv').open()))
samples = [json.loads(s) for s in (evidence / 'd2-http.jsonl').read_text().splitlines() if s]
reserved = {
    ('authorizeRealnameSensitiveView', 'REALNAME_CASE_INCONSISTENT'):
        ('application/service/material/VerificationSensitiveViews.java', 'realname() 查询申请、解密、审计、签名；不调用 VerificationCasework.associated，不产生案件一致性 reason。'),
    ('authorizeStudentSensitiveView', 'STUDENT_CASE_INCONSISTENT'):
        ('application/service/material/VerificationSensitiveViews.java', 'student() 查询申请及可授权实名、审计、签名；不查询案件关联，不产生案件一致性 reason。'),
    ('wechatPhoneLogin', 'PERMISSION_DENIED'):
        ('infrastructure/wechat/OfficialWechatGateway.java', '公共登录不经过 AdminAccessService；微信非指定凭证错误映射 WECHAT_UPSTREAM_UNAVAILABLE，账号限制为 ACCOUNT_ACTION_RESTRICTED，无 PERMISSION_DENIED 抛出路径。'),
}
for operation in ('recoverStudentSubmission', 'listStudentApplications'):
    reserved[(operation, 'CRYPTO_KEY_UNAVAILABLE')] = (
        'application/service/student/StudentQueryService.java',
        '该操作仅构建学生申请/列表，不调用实名解密；学生详情和敏感查看会解密，已另采 503，不能混为此操作。')
reserved[('submitStudent', 'CRYPTO_KEY_UNAVAILABLE')] = (
    'application/service/student/StudentSubmissionService.java',
    '学生提交/重放只处理学校名和对象键，不调用 VerificationCrypto；不因共用契约列出该 reason 就伪造命中。')
counts = {}
for row in matrix:
    key = (row['operationId'], row['reasonCode'])
    indexes = []
    pattern = re.sub(r'\{[^}]+\}', '[^/]+', row['path'])
    for number, sample in enumerate(samples, 1):
        if (sample['method'] == row['method'] and str(sample['status']) == row['http']
            and (sample['body'].get('reasonCode') or 'OK') == row['reasonCode']
            and re.fullmatch(pattern, sample['path'].split('?')[0])):
            indexes.append(number)
    row['sampleLines'] = ','.join(map(str, indexes[:12]))
    row['frontActionEvidence'] = '未逐组合完成原生操作；两域恢复见浏览器截图/DB证据，界面共同逻辑见 front-complete.log。不得按HTTP命中推定前端通过。'
    row['dbSessionEvidence'] = '按 D2ApplicationTest 对应场景断言；HTTP样本本身只证明响应，不独立证明所有SQL/会话最终状态。'
    if key == ('getCurrentStudent', 'CRYPTO_KEY_UNAVAILABLE') and indexes:
        row['conclusion'] = '修复前实际HTTP命中；修复后不再适用'
        row['conditionAndEvidence'] = 'C-16反例503已保留；current→currentStatus仅SELECT verification_status，不再解密。修复后无实名/有实名/缺key/受限账号定向HTTP通过；审核详情及敏感查看必要解密仍保留。见c16-student-current-before/after.json及c16-student-after.log。'
        row['dbSessionEvidence'] = '同一测试分别验证pending/rejected/verified实名、学生pending和受限/锁定账号；旧样本不倒改。'
    elif key == ('wechatPhoneLogin', 'WECHAT_UPSTREAM_UNAVAILABLE') and indexes:
        row['conclusion'] = '实际HTTP命中（受控上游）'
        row['conditionAndEvidence'] = 'OfficialWechatGateway真实JDK HTTP客户端→127.0.0.1受控500/异常响应/超时/断连，非真实微信自然故障。见c16-controlled-wechat-http.json、c16-upstream.log、c16-upstream-sample-boundaries.json。'
        row['dbSessionEvidence'] = '14故障场景均无账号/身份/规则接受记录新增；健康对照成功创建2身份和5规则接受记录。未替代真实手机号授权成功。'
    elif indexes:
        row['conclusion'] = '实际HTTP命中'
        row['conditionAndEvidence'] = 'd2-http.jsonl 对应行；替身/官方渠道见 externalBoundary。具体故障、SQL与会话断言见 C-15 验证登记和 D2ApplicationTest。'
    elif key in reserved:
        file, explanation = reserved[key]
        row['conclusion'] = '代码路径预留不可达（待总工复核）'
        row['conditionAndEvidence'] = 'src/main/java/com/mujian/backend/' + file + '；' + explanation
        row['dbSessionEvidence'] = '仅静态分析，未运行此 reason；不计通过、不删除契约。'
    elif key == ('wechatPhoneLogin', 'WECHAT_UPSTREAM_UNAVAILABLE'):
        row['conclusion'] = '依赖真实渠道待验'
        row['conditionAndEvidence'] = 'OfficialWechatGateway 非200、超时、非凭证errcode或响应字段异常路径；未主动中断真实微信；手机当前 getPhoneNumber no permission，尚未得到授权code。'
    else:
        row['conclusion'] = '未完成（不能归为不可达）'
        row['conditionAndEvidence'] = '仍需补可达故障条件与实际请求。'
    counts[row['conclusion']] = counts.get(row['conclusion'], 0) + 1
with (evidence / 'operation-conclusions.csv').open('w') as output:
    writer = csv.DictWriter(output, fieldnames=list(matrix[0]))
    writer.writeheader(); writer.writerows(matrix)
(evidence / 'operation-conclusions-summary.json').write_text(json.dumps(counts, ensure_ascii=False, indent=2)+'\n')
print(json.dumps(counts, ensure_ascii=False))
